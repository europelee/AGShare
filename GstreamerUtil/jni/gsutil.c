#include <string.h>
#include <jni.h>
#include <android/log.h>
#include <gst/gst.h>
#include <pthread.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include<sys/types.h>
#include <sys/mman.h>

GST_DEBUG_CATEGORY_STATIC( debug_category);
#define GST_CAT_DEFAULT debug_category

/*
 * These macros provide a way to store the native pointer to CustomData, which might be 32 or 64 bits, into
 * a jlong, which is always 64 bits, without warnings.
 */
#if GLIB_SIZEOF_VOID_P == 8
# define GET_CUSTOM_DATA(env, thiz, fieldID) (CustomData *)(*env)->GetLongField (env, thiz, fieldID)
# define SET_CUSTOM_DATA(env, thiz, fieldID, data) (*env)->SetLongField (env, thiz, fieldID, (jlong)data)
#else
# define GET_CUSTOM_DATA(env, thiz, fieldID) (CustomData *)(jint)(*env)->GetLongField (env, thiz, fieldID)
# define SET_CUSTOM_DATA(env, thiz, fieldID, data) (*env)->SetLongField (env, thiz, fieldID, (jlong)(jint)data)
#endif

#define CHUNK_SIZE  102400   /* Amount of bytes we are sending in each buffer */

/* Structure to contain all our information, so we can pass it to callbacks */
typedef struct _CustomData {
	jobject app; /* Application instance, used to call its methods. A global reference is kept. */
	GstElement *pipeline; /* The running pipeline */
	GMainContext *context; /* GLib context used to run the main loop */
	GMainLoop *main_loop; /* GLib main loop */
	gboolean initialized; /* To avoid informing the UI multiple times about the initialization */
	GstElement *app_source;

} CustomData;

/* These global variables cache values which are not changing during execution */
static pthread_t gst_app_thread;
static pthread_key_t current_jni_env;
static JavaVM *java_vm;
static jfieldID custom_data_field_id;
static jmethodID set_message_method_id;
static jmethodID on_gstreamer_initialized_method_id;

static int iEnd = 1;
static const char * TAGSTR = "gsutil";

static char SHM_FILE[PATH_MAX] = { 0 };

static unsigned char *head_maped = NULL;

//mean max file length 4G
static unsigned long recv_len = 0;

static unsigned long in_index = 0; //next input index
static unsigned long out_index = 0;

// Mutex instance
static pthread_mutex_t * pmutex = NULL;

/*
 * Private methods
 */

/* Register this thread with the VM */
static JNIEnv *attach_current_thread(void) {
	JNIEnv *env;
	JavaVMAttachArgs args;

	GST_DEBUG("Attaching thread %p", g_thread_self());
	args.version = JNI_VERSION_1_4;
	args.name = NULL;
	args.group = NULL;

	if ((*java_vm)->AttachCurrentThread(java_vm, &env, &args) < 0) {
		GST_ERROR("Failed to attach current thread");
		return NULL;
	}

	return env;
}

/* Unregister this thread from the VM */
static void detach_current_thread(void *env) {
	GST_DEBUG("Detaching thread %p", g_thread_self());
	(*java_vm)->DetachCurrentThread(java_vm);
}

/* Retrieve the JNI environment for this thread */
static JNIEnv *get_jni_env(void) {
	JNIEnv *env;

	if ((env = pthread_getspecific(current_jni_env)) == NULL) {
		env = attach_current_thread();
		pthread_setspecific(current_jni_env, env);
	}

	return env;
}

/* Change the content of the UI's TextView */
static void set_ui_message(const gchar *message, CustomData *data) {
	JNIEnv *env = get_jni_env();
	GST_DEBUG("Setting message to: %s", message);
	jstring jmessage = (*env)->NewStringUTF(env, message);
	(*env)->CallVoidMethod(env, data->app, set_message_method_id, jmessage);
	if ((*env)->ExceptionCheck(env)) {
		GST_ERROR("Failed to call Java method");
		(*env)->ExceptionClear(env);
	}
	(*env)->DeleteLocalRef(env, jmessage);
}

static void closefd(int *fd, const char * fdinfo) {
	__android_log_print(ANDROID_LOG_INFO, TAGSTR, "close fd %d, fdinfo:%s", *fd,
			fdinfo);

	if (-1 == *fd)
		return;

	int ret = 0;
	if (-1 != *fd) {
		ret = close(*fd);
		if (0 != ret) {
			__android_log_print(ANDROID_LOG_ERROR, TAGSTR,
					"close(in_fd) fail, errno:%d", errno);
		} else {
			*fd = -1;
		}
	}
}

static void fin_shmfile() {
	__android_log_print(ANDROID_LOG_INFO, TAGSTR, "fin_shmfile start: ");

	/**
	 * The munmap() system call deletes the mappings for the specified address range,
	 and causes further references to addresses within the range to
	 generate invalid memory references. The region is also automatically
	 unmapped when the process is terminated. On the other hand,
	 closing the file descriptor does not unmap the region
	 */
	if ((NULL != head_maped)
			&& ((munmap((void *) head_maped, recv_len)) == -1)) {
		__android_log_print(ANDROID_LOG_ERROR, TAGSTR,
				"error munmap SHM_FILE: %d", errno);
		perror("munmap");
	}

	head_maped = NULL;

	if (0 == access(SHM_FILE, F_OK)) {
		int ret = remove(SHM_FILE);
		if (0 != ret) {
			__android_log_print(ANDROID_LOG_ERROR, TAGSTR,
					"remove %s fail, errno:%d", SHM_FILE, errno);
		}
	} else {
		__android_log_print(ANDROID_LOG_INFO, TAGSTR, "%s not exist!",
				SHM_FILE);
	}
}

static gboolean push_data(CustomData *data) {

	g_print("gsutil:push_data enter");

	//avoiding pushing data again when finish but enter eos_cb not yet
	if (out_index >= recv_len) {
		__android_log_print(ANDROID_LOG_INFO, TAGSTR, "already finish playing");
		return FALSE;
	}

	GstBuffer *buffer = NULL;
	GstFlowReturn ret;

	guint8 *raw = NULL;

#if 0
	time_t begin = time(NULL);
	__android_log_print(ANDROID_LOG_INFO, TAGSTR,
			"begin time is  %ld", begin);
#endif

	int nRead = 0;
	if (out_index + CHUNK_SIZE > recv_len) {
		//memcpy(raw, head_maped + out_index, recv_len - out_index);
		//out_index = recv_len;
		int nLoop = 60;
		while (1) {
			if (in_index == recv_len) {
				g_print("only small chunk now!");
				nRead = recv_len - out_index;
				break;
			}
			sleep(1);
			nLoop--;
			if (nLoop <= 0) {
				__android_log_print(ANDROID_LOG_ERROR, TAGSTR,
						"get small chunk  data  timeout after 6s!");
				return FALSE;
			} else {
				continue;
			}
		}//WHILE
	} else {
		int nLoop = 60;
		while (1) {
			if (NULL != pmutex) {
				if (0 != pthread_mutex_lock(pmutex)) {
					g_print("uupthread_mutex_lock fail!");
				} else {
					g_print("uupthread_mutex_lock succ!");
				}
			} else {
				g_print("pmutex is null!");
			}

			if (out_index + CHUNK_SIZE > in_index) {

				if (NULL != pmutex) {
					if (0 != pthread_mutex_unlock(pmutex)) {
						g_print("uupthread_mutex_UNlock fail!");
					} else {
						g_print("uupthread_mutex_UNlock succ!");
					}
				} else {
					g_print("pmutex is null!");
				}

				__android_log_print(ANDROID_LOG_INFO, TAGSTR,
						"buff readable is empty!");

				sleep(1);
				nLoop--;
				if (nLoop <= 0) {
					__android_log_print(ANDROID_LOG_ERROR, TAGSTR,
							"get CHUNK_SIZE data  timeout after 6s!");

					//gst_buffer_unref(buffer);

					return FALSE;
				} else {
					continue;
				}
				//return FALSE;
			} else {
				if (NULL != pmutex) {
					if (0 != pthread_mutex_unlock(pmutex)) {
						g_print("uupthread_mutex_UNlock fail!");
					} else {
						g_print("uupthread_mutex_UNlock succ!");
					}
				} else {
					g_print("pmutex is null!");
				}

				break;
			}

		} //while

		nRead = CHUNK_SIZE;

	}

	/* Create a new empty buffer */
	buffer = gst_buffer_new_and_alloc(nRead);//CHUNK_SIZE

	raw = (guint8 *) GST_BUFFER_DATA(buffer);

	memcpy(raw, head_maped + out_index, nRead);//CHUNK_SIZE

	out_index += nRead;//CHUNK_SIZE

	if (out_index == recv_len) {
		__android_log_print(ANDROID_LOG_INFO, TAGSTR, "finish reading");

		iEnd = 1;
	}

#if 0
	time_t end = time(NULL);
	__android_log_print(ANDROID_LOG_INFO, TAGSTR,
			"end time is  %ld", end);

	__android_log_print(ANDROID_LOG_INFO, TAGSTR,
			"end-begin is  %ld", (long)end-(long)begin);
#endif
	/* Push the buffer into the appsrc */
	g_signal_emit_by_name(data->app_source, "push-buffer", buffer, &ret);

	/* Free the buffer now that we are done with it */
	gst_buffer_unref(buffer);

	if (ret != GST_FLOW_OK) {
		/* We got some error, stop sending data */
		__android_log_print(ANDROID_LOG_ERROR, TAGSTR,
				"push-buffer fail, ret is %d", ret);
		return FALSE;
	}

	//end-of-stream
	if (1 == iEnd) {
		ret = gst_app_src_end_of_stream(data->app_source);
		if (ret == GST_FLOW_OK)
			g_print("gst_app_src_end_of_stream succ");
	}

	return TRUE;
}

/* This signal callback triggers when appsrc needs data. Here, we add an idle handler * to the mainloop to start pushing data into the appsrc */
static void start_feed(GstElement *source, guint size, CustomData *data) {

	//g_print("Start feeding\n");
	push_data(data);

}
/* This callback triggers when appsrc has enough data and we can stop sending. * We remove the idle handler from the mainloop */
static void stop_feed(GstElement *source, CustomData *data) {

	g_print("Stop feeding\n");

}

/* This function is called when playbin2 has created the appsrc element, so we have * a chance to configure it. */
static void source_setup(GstElement *pipeline, GstElement *source,
		CustomData *data) {

	g_print("Source has been created. Configuring.\n");
	data->app_source = source; /* Configure appsrc */

	//any means any
	GstCaps * gany;
	gany = gst_caps_new_any();
	g_object_set(source, "caps", gany, NULL);
	gst_caps_unref(gany);

	g_signal_connect(source, "need-data", G_CALLBACK(start_feed), data);
	g_signal_connect(source, "enough-data", G_CALLBACK(stop_feed), data);

}

/* Retrieve errors from the bus and show them on the UI */
static void error_cb(GstBus *bus, GstMessage *msg, CustomData *data) {
	GError *err;
	gchar *debug_info;
	gchar *message_string;

	gst_message_parse_error(msg, &err, &debug_info);
	message_string = g_strdup_printf("Error received from element %s: %s",
			GST_OBJECT_NAME(msg->src), err->message);
	g_clear_error(&err);
	g_free(debug_info);
	set_ui_message(message_string, data);
	g_free(message_string);
	gst_element_set_state(data->pipeline, GST_STATE_NULL);
}

/* Notify UI about pipeline state changes */
static void state_changed_cb(GstBus *bus, GstMessage *msg, CustomData *data) {
	GstState old_state, new_state, pending_state;
	gst_message_parse_state_changed(msg, &old_state, &new_state,
			&pending_state);
	/* Only pay attention to messages coming from the pipeline, not its children */
	if (GST_MESSAGE_SRC(msg) == GST_OBJECT(data->pipeline)) {

		g_print("state_changed_cb: %s\n",
				gst_element_state_get_name(new_state));

		gchar *message = g_strdup_printf("State changed to %s",
				gst_element_state_get_name(new_state));
		set_ui_message(message, data);
		g_free(message);
	} else {
		g_print("msg from %s, state changed to %s\n",
				GST_OBJECT_NAME(GST_MESSAGE_SRC(msg)),
				gst_element_state_get_name(new_state));

	}

}

/* Check if all conditions are met to report GStreamer as initialized.
 * These conditions will change depending on the application */
static void check_initialization_complete(CustomData *data) {
	JNIEnv *env = get_jni_env();
	if (!data->initialized && data->main_loop) {
		GST_DEBUG(
				"Initialization complete, notifying application. main_loop:%p",
				data->main_loop);
		(*env)->CallVoidMethod(env, data->app,
				on_gstreamer_initialized_method_id);
		if ((*env)->ExceptionCheck(env)) {
			GST_ERROR("Failed to call Java method");
			(*env)->ExceptionClear(env);
		}
		data->initialized = TRUE;
	}
}

static void eos_cb(GstBus *bus, GstMessage *msg, CustomData *data) {

	g_print("eos_cb called!");

	data->initialized = FALSE;
	gst_element_set_state(data->pipeline, GST_STATE_PAUSED);
	g_main_loop_quit(data->main_loop);

}

/* Main method for the native code. This is executed on its own thread. */
static void *app_function(void *userdata) {

	__android_log_print(ANDROID_LOG_INFO, TAGSTR, "app_function start");

	in_index = 0;
	out_index = 0;

	// Initialize mutex
	//it need this thread start first before alljoyn calling input data
	if (NULL != pmutex) {
		free(pmutex);
		pmutex = NULL;
	}

	pmutex = (pthread_mutex_t *) malloc(sizeof(pthread_mutex_t));

	if (0 != pthread_mutex_init(pmutex, NULL)) {

		g_print("pthread_mutex_init fail!");

	} else {
		g_print("pthread_mutex_init succ!");
	}

	//
	JavaVMAttachArgs args;
	GstBus *bus;
	CustomData *data = (CustomData *) userdata;

	GSource *bus_source;
	GError *error = NULL;

	GST_DEBUG("Creating pipeline in CustomData at %p", data);

	/* Create our own GLib Main Context and make it the default one */
	data->context = g_main_context_new();
	g_main_context_push_thread_default(data->context);

	/* Build pipeline */
	data->pipeline = gst_parse_launch("playbin2 uri=appsrc://", &error);

	if (error) {
		gchar *message = g_strdup_printf("Unable to build pipeline: %s",
				error->message);
		g_clear_error(&error);
		set_ui_message(message, data);
		g_free(message);

		// Destory mutex
		if ((NULL != pmutex) && (0 != pthread_mutex_destroy(pmutex))) {
			g_print("pthread_mutex_destroy fail!");
		} else {
			g_print("pthread_mutex_destroy succ!");
		}

		if (NULL != pmutex) {
			free(pmutex);
			pmutex = NULL;
		}

		return NULL;
	}

	g_signal_connect(data->pipeline, "source-setup", G_CALLBACK(source_setup),
			data);

	/* Instruct the bus to emit signals for each received message, and connect to the interesting signals */
	bus = gst_element_get_bus(data->pipeline);
	bus_source = gst_bus_create_watch(bus);
	g_source_set_callback(bus_source, (GSourceFunc) gst_bus_async_signal_func,
			NULL, NULL);
	g_source_attach(bus_source, data->context);
	g_source_unref(bus_source);

	g_signal_connect(G_OBJECT(bus), "message::eos", (GCallback) eos_cb, data);

	g_signal_connect(G_OBJECT(bus), "message::error", (GCallback) error_cb,
			data);
	g_signal_connect(G_OBJECT(bus), "message::state-changed",
			(GCallback) state_changed_cb, data);
	gst_object_unref(bus);

	/* Create a GLib Main Loop and set it to run */
	GST_DEBUG("Entering main loop... (CustomData:%p)", data);
	data->main_loop = g_main_loop_new(data->context, FALSE);
	check_initialization_complete(data);

	/* Start playing the pipeline */
	//gst_element_set_state(data->pipeline, GST_STATE_PLAYING);
	g_main_loop_run(data->main_loop);

	GST_DEBUG("Exited main loop");
	g_main_loop_unref(data->main_loop);
	data->main_loop = NULL;

	/* Free resources */
	g_main_context_pop_thread_default(data->context);
	g_main_context_unref(data->context);
	gst_element_set_state(data->pipeline, GST_STATE_NULL);
	gst_object_unref(data->pipeline);

	fin_shmfile();

	// Destory mutex
	if ((NULL != pmutex) && (0 != pthread_mutex_destroy(pmutex))) {
		g_print("pthread_mutex_destroy fail!");
	} else {
		g_print("pthread_mutex_destroy succ!");
	}

	if (NULL != pmutex) {
		free(pmutex);
		pmutex = NULL;
	}

	in_index = 0;
	out_index = 0;

	return NULL;
}

/**
 *init_shmfile
 *return true: create file succ, and get  head_mapped
 */
static gboolean init_shmfile(JNIEnv* env) {

	if (0UL == recv_len) {
		__android_log_print(ANDROID_LOG_ERROR, TAGSTR, "recv_len: %lu, ",
				recv_len);
		return FALSE;
	}

	jclass cls_Env = (*env)->FindClass(env, "android/os/Environment");
	jmethodID mid_getExtStorage = (*env)->GetStaticMethodID(env, cls_Env,
			"getExternalStorageDirectory", "()Ljava/io/File;");
	jobject obj_File = (*env)->CallStaticObjectMethod(env, cls_Env,
			mid_getExtStorage);

	jclass cls_File = (*env)->FindClass(env, "java/io/File");
	jmethodID mid_getPath = (*env)->GetMethodID(env, cls_File, "getPath",
			"()Ljava/lang/String;");
	jstring obj_Path = (*env)->CallObjectMethod(env, obj_File, mid_getPath);
	const char* path = (*env)->GetStringUTFChars(env, obj_Path, 0);

	snprintf(SHM_FILE, PATH_MAX, "%s/media_shm", path);

	(*env)->ReleaseStringUTFChars(env, obj_Path, path);

	g_print("SHM_FILE path:%s", SHM_FILE);

	int fd = -1;
	//O_EXCL for exclude case: another process/thread still open and do some job
	if ((fd = open(SHM_FILE, O_RDWR | O_CREAT | O_EXCL)) == -1) {
		if (errno != EEXIST) {
			__android_log_print(ANDROID_LOG_ERROR, TAGSTR, "open fail: %d",
					errno);
			return FALSE;
		} else {
			__android_log_print(ANDROID_LOG_INFO, TAGSTR, "file %s exist!",
					SHM_FILE);
			int ret = remove(SHM_FILE);
			if (0 != ret) {
				__android_log_print(ANDROID_LOG_ERROR, TAGSTR,
						"remove %s fail, errno:%d", SHM_FILE, errno);
				return FALSE;
			} else {
				fd = open(SHM_FILE, O_RDWR | O_CREAT | O_EXCL);
				if (-1 == fd) {
					__android_log_print(ANDROID_LOG_ERROR, TAGSTR,
							"open fail again!: %d ", errno);
					return FALSE;
				}
			}
		}
	}

	if (ftruncate(fd, recv_len) == -1) {
		__android_log_print(ANDROID_LOG_ERROR, TAGSTR,
				"error truncate the file: %d", errno);

		close(fd);

		return FALSE;
	}

	head_maped = mmap(0, recv_len, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);

	if (head_maped == MAP_FAILED || head_maped == NULL) {
		__android_log_print(ANDROID_LOG_ERROR, TAGSTR,
				"error mmap SHM_FILE: %d", errno);
		perror("mmap");
		close(fd);
		return FALSE;
	} else {
		__android_log_print(ANDROID_LOG_INFO, TAGSTR, "mmap succ!");
	}
	return TRUE;
}

/*
 * Java Bindings
 */

/* Instruct the native code to create its internal data structure, pipeline and thread */
static void gst_native_init(JNIEnv* env, jobject thiz) {

	g_print("new CustomData start");
	CustomData *data = g_new0(CustomData, 1);
	g_print("new CustomData end");
	SET_CUSTOM_DATA(env, thiz, custom_data_field_id, data);
	GST_DEBUG_CATEGORY_INIT(debug_category, "gstreamerutil", 0, "gsutil");
	gst_debug_set_threshold_for_name("gstreamerutil", GST_LEVEL_DEBUG);
	g_print("Created CustomData at %p", data);

	data->app = (*env)->NewGlobalRef(env, thiz);
	g_print("Created GlobalRef for app object at %p", data->app);

	//pthread_create(&gst_app_thread, NULL, &app_function, data);

}

/* Quit the main loop, remove the native thread and free resources */
static void gst_native_finalize(JNIEnv* env, jobject thiz) {

	CustomData *data = GET_CUSTOM_DATA (env, thiz, custom_data_field_id);
	if (!data)
		return;

	if (0 == iEnd) {
		GST_DEBUG("Quitting main loop...");
		g_main_loop_quit(data->main_loop);
		GST_DEBUG("Waiting for thread to finish...");
		pthread_join(gst_app_thread, NULL);

		iEnd = 1;
	}

	//anothread may use fd, so join it first
	fin_shmfile();

	GST_DEBUG("Deleting GlobalRef for app object at %p", data->app);
	(*env)->DeleteGlobalRef(env, data->app);
	GST_DEBUG("Freeing CustomData at %p", data);
	g_free(data);
	SET_CUSTOM_DATA(env, thiz, custom_data_field_id, NULL);
	GST_DEBUG("Done finalizing");
}

/* Set pipeline to PLAYING state */
static void gst_native_play(JNIEnv* env, jobject thiz) {

	CustomData *data = GET_CUSTOM_DATA (env, thiz, custom_data_field_id);
	if (!data) {
		g_print("data is null!\n");
		return;
	}
	GST_DEBUG("Setting state to PLAYING");
	g_print("data is Setting state to PLAYING!");

	if (iEnd == 1) {

		iEnd = 0;
		//check pipleline
		//if (-1 == in_fd || -1 == out_fd)

		//processing unexception
		{
			fin_shmfile();
			gboolean ans = init_shmfile(env);
			if (FALSE == ans) {
				iEnd = 1;
				return;
			}
		}

		pthread_create(&gst_app_thread, NULL, &app_function, data);

	}

	//check pipeline is ok(it generated in another thread)
	// then go on
	int nLoop = 20;
	while (FALSE == data->initialized) {
		usleep(200000);
		nLoop--;
		if (nLoop <= 0) {
			__android_log_print(ANDROID_LOG_ERROR, TAGSTR,
					"inital gstreamer data timeout!");
			break;
		}
	}

	GstStateChangeReturn ret;

	ret = gst_element_set_state(data->pipeline, GST_STATE_PLAYING);
	if (ret == GST_STATE_CHANGE_FAILURE) {
		g_print("Unable to set the pipeline to the playing state.\n");
		return;
	} else if (ret == GST_STATE_CHANGE_NO_PREROLL) {
		g_print(" data.is_live = TRUE.\n");

	} else if (ret == GST_STATE_CHANGE_SUCCESS) {
		g_print(" GST_STATE_CHANGE_SUCCESS.\n");
	}

	g_print("rest is %d", ret);

}

/* Set pipeline to PAUSED state */
static void gst_native_pause(JNIEnv* env, jobject thiz) {
	CustomData *data = GET_CUSTOM_DATA (env, thiz, custom_data_field_id);
	if (!data)
		return;
	GST_DEBUG("Setting state to PAUSED");
	gst_element_set_state(data->pipeline, GST_STATE_PAUSED);
}

static void gst_native_inputdata(JNIEnv* env, jobject thiz, jbyteArray jbarray) {

	if (NULL == head_maped) {
		__android_log_print(ANDROID_LOG_ERROR, TAGSTR,
				"could not inputdata, head_maped null");
		return;
	}

	int nArrLen = (*env)->GetArrayLength(env, jbarray);
	unsigned char *chArr = (unsigned char *) ((*env)->GetByteArrayElements(env,
			jbarray, 0));

	time_t begin = time(NULL);
	__android_log_print(ANDROID_LOG_INFO, TAGSTR, "begin time is  %ld", begin);

	//care number overflow!
	if (in_index + nArrLen <= recv_len)
		memcpy(head_maped + in_index, chArr, nArrLen);
	else {
		__android_log_print(ANDROID_LOG_ERROR, TAGSTR,
				"in_index+nArrLen %ld > recv_len %ld", in_index + nArrLen,
				recv_len);

		return;
	}

	if (NULL != pmutex) {
		if (0 != pthread_mutex_lock(pmutex)) {
			g_print("uupthread_mutex_lock fail!");
		} else {
			g_print("uupthread_mutex_lock succ!");
		}
	} else {
		g_print("pmutex is null!");
	}

	in_index += nArrLen;

	if (NULL != pmutex) {
		if (0 != pthread_mutex_unlock(pmutex)) {
			g_print("uupthread_mutex_UNlock fail!");
		} else {
			g_print("uupthread_mutex_UNlock succ!");
		}
	} else {
		g_print("pmutex is null!");
	}

	time_t end = time(NULL);
	__android_log_print(ANDROID_LOG_INFO, TAGSTR, "end time is  %ld", end);

	__android_log_print(ANDROID_LOG_INFO, TAGSTR, "end-begin is  %ld",
			(long) end - (long) begin);

	//

	if (in_index == recv_len) {
		__android_log_print(ANDROID_LOG_INFO, TAGSTR,
				"finish gst_native_inputdata!!");
	}

	(*env)->ReleaseByteArrayElements(env, jbarray, chArr, 0);
}

static jboolean gst_native_setrecvlen(JNIEnv* env, jobject thiz, jlong jlen) {

	//java just support long, use BigInteger future
	recv_len = jlen;
	__android_log_print(ANDROID_LOG_INFO, TAGSTR, "play byteslen :%ld",
			recv_len);

	//gboolean ans = init_shmfile(env);

	return TRUE;

}

/* Static class initializer: retrieve method and field IDs */
static jboolean gst_native_class_init(JNIEnv* env, jclass klass) {
	custom_data_field_id = (*env)->GetFieldID(env, klass, "native_custom_data",
			"J");
	set_message_method_id = (*env)->GetMethodID(env, klass, "setMessage",
			"(Ljava/lang/String;)V");
	on_gstreamer_initialized_method_id = (*env)->GetMethodID(env, klass,
			"onGStreamerInitialized", "()V");

	if (!custom_data_field_id || !set_message_method_id
			|| !on_gstreamer_initialized_method_id) {
		/* We emit this message through the Android log instead of the GStreamer log because the later
		 * has not been initialized yet.
		 */
		__android_log_print(ANDROID_LOG_ERROR, TAGSTR,
				"The calling class does not implement all necessary interface methods");
		return JNI_FALSE;
	}
	return JNI_TRUE;
}

/* List of implemented native methods */
static JNINativeMethod native_methods[] = { { "nativeInit", "()V",
		(void *) gst_native_init }, { "nativeFinalize", "()V",
		(void *) gst_native_finalize }, { "nativePlay", "()V",
		(void *) gst_native_play }, { "nativePause", "()V",
		(void *) gst_native_pause }, { "nativeInputData", "([B)V",
		(void *) gst_native_inputdata }, { "nativeSetRecvLen", "(J)Z",
		(void *) gst_native_setrecvlen }, { "nativeClassInit", "()Z",
		(void *) gst_native_class_init } };

/* Library initializer */
jint JNI_OnLoad(JavaVM *vm, void *reserved) {
	JNIEnv *env = NULL;

	java_vm = vm;

	if ((*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_4) != JNI_OK) {
		__android_log_print(ANDROID_LOG_ERROR, TAGSTR,
				"Could not retrieve JNIEnv");
		return 0;
	}
	jclass klass = (*env)->FindClass(env,
			"org/upnp/gstreamerutil/GstUtilNative");

	(*env)->RegisterNatives(env, klass, native_methods,
			G_N_ELEMENTS(native_methods));

	pthread_key_create(&current_jni_env, detach_current_thread);

	return JNI_VERSION_1_4;
}
