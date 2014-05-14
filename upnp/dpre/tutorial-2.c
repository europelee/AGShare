#include <string.h>
#include <jni.h>
#include <android/log.h>
#include <gst/gst.h>
#include <pthread.h>
#include<sys/types.h>
#include<sys/stat.h>
#include<errno.h>
#include <unistd.h>
#include <fcntl.h>
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

#define CHUNK_SIZE 1024   /* Amount of bytes we are sending in each buffer */
#define SAMPLE_RATE 44100 /* Samples per second we are sending */
//#define AUDIO_CAPS "audio/x-raw-int,channels=1,rate=%d,signed=(boolean)true,width=16,depth=16,endianness=BYTE_ORDER"
//#define AUDIO_CAPS "audio/x-raw-int,channels=1,rate=%d,signed=(boolean)true,width=16,depth=16,endianness=BYTE_ORDER"
#define AUDIO_CAPS "audio/mpeg, mpegversion=1, mpegaudioversion=1, layer=3, rate=%d, channels=1,parsed=(boolean)true"
/* Structure to contain all our information, so we can pass it to callbacks */
typedef struct _CustomData {
	jobject app; /* Application instance, used to call its methods. A global reference is kept. */
	GstElement *pipeline; /* The running pipeline */
	GMainContext *context; /* GLib context used to run the main loop */
	GMainLoop *main_loop; /* GLib main loop */
	gboolean initialized; /* To avoid informing the UI multiple times about the initialization */

	GstElement *app_source;
	guint64 num_samples; /* Number of samples generated so far (for timestamp generation) */
	gfloat a, b, c, d; /* For waveform generation */
	guint sourceid; /* To control the GSource */

	GMappedFile *file;
	guint8 *data;
	gsize length;
	guint64 offset;

} CustomData;

/* These global variables cache values which are not changing during execution */
static pthread_t gst_app_thread;
static pthread_key_t current_jni_env;
static JavaVM *java_vm;
static jfieldID custom_data_field_id;
static jmethodID set_message_method_id;
static jmethodID on_gstreamer_initialized_method_id;

// Mutex instance
static pthread_mutex_t mutex;

static int iEnd = 0;

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

static gboolean push_data(CustomData *data) {

	GstBuffer *buffer;
	GstFlowReturn ret;
	int i;
	guint8 *raw;
	gint num_samples = CHUNK_SIZE / 2;
	/* Because each sample is 16 bits */gfloat freq; /* Create a new empty
	 buffer */
	buffer = gst_buffer_new_and_alloc(CHUNK_SIZE); /* Set its
	 timestamp and duration */
	/*
	 GST_BUFFER_TIMESTAMP (buffer) = gst_util_uint64_scale(data->num_samples,
	 GST_SECOND, SAMPLE_RATE);
	 GST_BUFFER_DURATION (buffer) = gst_util_uint64_scale(CHUNK_SIZE, GST_SECOND,
	 SAMPLE_RATE);
	 */
	/* Generate some psychodelic waveforms */
	g_print("GST_BUFFER_TIMESTAMP:%llu, GST_BUFFER_DURATION:%llu",
			GST_BUFFER_TIMESTAMP(buffer), GST_BUFFER_DURATION(buffer));

	//raw = (gint16 *) GST_BUFFER_DATA(buffer);
	raw = (guint8 *) GST_BUFFER_DATA(buffer);
	//GST_BUFFER_SIZE (buffer) = CHUNK_SIZE;

	/*
	 data->c += data->d;
	 data->d -= data->c / 1000;
	 freq = 1100 + 1000 * data->d;
	 for (i = 0; i < num_samples; i++) {
	 data->a += data->b;
	 data->b -= data->a / freq;
	 raw[i] = (gint16)(500 * data->a);
	 }
	 */

	static FILE * pf = NULL;

	if (NULL == pf)
		pf = fopen("/storage/sdcard0/x.mp3", "rb");

	if (NULL == pf) {
		g_print("pf is null !!!!!!!!!!!!1");
		return FALSE;
	}

	if (0 == feof(pf)) {
		size_t sRead = fread(raw, sizeof(guint8), CHUNK_SIZE, pf);
		g_print("read %d......", sRead);
	} else {
		iEnd = 1;
		fclose(pf);
		pf = NULL;
		g_print("finish reading!!!!!!!");
		/* we are EOS, send end-of-stream */
		g_signal_emit_by_name(data->app_source, "end-of-stream", &ret);

		return FALSE;
	}
	//////////////////////////////////////////////////////////////////
	data->num_samples += num_samples; /* Push the buffer into the
	 appsrc */
	g_signal_emit_by_name(data->app_source, "push-buffer", buffer, &ret); /* Free the buffer now that we are done with it */
	gst_buffer_unref(buffer);
	if (ret != GST_FLOW_OK) { /* We got some
	 error, stop sending data */
		g_print("push_data FAIL!!!!!!!!!!!!!!!!!!!!:: %d\n", ret);
		return FALSE;
	}
	return TRUE;
}

/* This signal callback triggers when appsrc needs data. Here, we add an idle handler * to the mainloop to start pushing data into the appsrc */
static void start_feed(GstElement *source, guint size, CustomData *data) {
	if (data->sourceid == 0) {
		g_print("Start feeding\n");

		push_data(data);
		//data->sourceid = 1;
		//data->sourceid = g_idle_add((GSourceFunc) push_data, data);

		g_print("data->sourceid :%u", data->sourceid);
	}
}
/* This callback triggers when appsrc has enough data and we can stop sending. * We remove the idle handler from the mainloop */
static void stop_feed(GstElement *source, CustomData *data) {

	g_print("Stop feedingqqqqq\n");
	if (data->sourceid != 0) {
		g_print("Stop feeding\n");
		g_source_remove(data->sourceid);
		data->sourceid = 0;
	}
}

/* This function is called when playbin2 has created the appsrc element, so we have * a chance to configure it. */
static void source_setup(GstElement *pipeline, GstElement *source,
		CustomData *data) {
	gchar *audio_caps_text;
	GstCaps *audio_caps;
	g_print("Source has been created. Configuring.\n");
	data->app_source = source; /* Configure appsrc */
	audio_caps_text = g_strdup_printf(AUDIO_CAPS, SAMPLE_RATE);
	audio_caps = gst_caps_from_string(audio_caps_text);
	g_object_set(source, "caps", audio_caps, NULL);
	g_signal_connect(source, "need-data", G_CALLBACK(start_feed), data);
	g_signal_connect(source, "enough-data", G_CALLBACK(stop_feed), data);
	gst_caps_unref(audio_caps);
	g_free(audio_caps_text);
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

/* Main method for the native code. This is executed on its own thread. */
static void *app_function(void *userdata) {

	//test
	int fd;
	int ret;

	void * pbuf = malloc(sizeof(unsigned char) * 1024);
	memset(pbuf, 0xef, sizeof(unsigned char) * 1024);

	fd = open("/storage/sdcard0/fifo9000", O_WRONLY);
	if (-1 == fd) {
		g_print("/storage/sdcard0/fifo9000 open error/n");
		return;
	}
	while (TRUE) {
		/*
		 if (0 != pthread_mutex_lock(&mutex))
		 {
		 g_print("pthread_mutex_lock fail!");
		 }
		 else
		 {
		 g_print("pthread_mutex_lock succ!");
		 }
		 sleep(60);
		 g_print("thread finish sleeping 10s");
		 // Unlock mutex
		 if (0 != pthread_mutex_unlock(&mutex))
		 {
		 g_print("pthread_mutex_unlock fail!");
		 }
		 else
		 {
		 g_print("pthread_mutex_lock succ!");
		 }

		 sleep(10);
		 g_print("after unlocking, thread finish sleeping 10s");
		 */

		write(fd, pbuf, sizeof(unsigned char) * 1024);
		sleep(10);

	}

	return;

	//
	JavaVMAttachArgs args;
	GstBus *bus;
	CustomData *data = (CustomData *) userdata;

	data->b = 1; /* For waveform generation */
	data->d = 1;

	GSource *bus_source;
	GError *error = NULL;

	GST_DEBUG("Creating pipeline in CustomData at %p", data);

	/* Create our own GLib Main Context and make it the default one */
	data->context = g_main_context_new();
	g_main_context_push_thread_default(data->context);

	/* Build pipeline */
//  data->pipeline = gst_parse_launch("audiotestsrc ! audioconvert ! audioresample ! autoaudiosink", &error);
//  data->pipeline = gst_parse_launch("filesrc location=/storage/sdcard0/x.mp3 ! mad ! audioconvert ! audioresample ! autoaudiosink", &error);
//  data->pipeline = gst_parse_launch("playbin2 uri=file:///storage/sdcard0/x.mp3", NULL);
	data->pipeline = gst_parse_launch("playbin2 uri=appsrc://", &error);
//	data->pipeline = gst_parse_launch("appsrc name=mysource ! audio/mpeg ! mad ! audioconvert ! audioresample ! autoaudiosink", &error);
//	data->pipeline = gst_parse_launch("filesrc location=/storage/sdcard0/x.mp3 ! mpegaudioparse  ! mad ! audioconvert ! audioresample ! autoaudiosink", &error);

	if (error) {
		gchar *message = g_strdup_printf("Unable to build pipeline: %s",
				error->message);
		g_clear_error(&error);
		set_ui_message(message, data);
		g_free(message);
		return NULL;
	}

	g_signal_connect(data->pipeline, "source-setup", G_CALLBACK(source_setup),
			data);
	/* get the appsrc */
	/*
	 data->app_source = gst_bin_get_by_name (GST_BIN(data->pipeline), "mysource");
	 g_assert(data->app_source);
	 // g_assert(GST_IS_APP_SRC(data->app_source));
	 g_signal_connect (data->app_source, "need-data", G_CALLBACK (start_feed), data);
	 g_signal_connect (data->app_source, "enough-data", G_CALLBACK (stop_feed), data);
	 */

	/* Instruct the bus to emit signals for each received message, and connect to the interesting signals */
	bus = gst_element_get_bus(data->pipeline);
	bus_source = gst_bus_create_watch(bus);
	g_source_set_callback(bus_source, (GSourceFunc) gst_bus_async_signal_func,
			NULL, NULL);
	g_source_attach(bus_source, data->context);
	g_source_unref(bus_source);
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
	//gst_element_set_state (data->pipeline, GST_STATE_PLAYING);

	g_main_loop_run(data->main_loop);
	GST_DEBUG("Exited main loop");
	g_main_loop_unref(data->main_loop);
	data->main_loop = NULL;

	/* Free resources */
	g_main_context_pop_thread_default(data->context);
	g_main_context_unref(data->context);
	gst_element_set_state(data->pipeline, GST_STATE_NULL);
	gst_object_unref(data->pipeline);

	return NULL;
}

/*
 * Java Bindings
 */

/* Instruct the native code to create its internal data structure, pipeline and thread */
static void gst_native_init(JNIEnv* env, jobject thiz) {

	if (-1 == access("/storage/sdcard0/fifo9000", F_OK)) {
		int res = mkfifo("/storage/sdcard0/fifo9000", S_IRWXO);
		if (res != 0) {
			g_print("Error while creating a pipe (return:%d, errno:%d)", res,
					errno);
		}
	}
	else
	{

	}
	// Initialize mutex
	if (0 != pthread_mutex_init(&mutex, NULL)) {

		// Get the exception class
		jclass exceptionClazz = (*env)->FindClass(env,
				"java/lang/RuntimeException");
		// Throw exception
		(*env)->ThrowNew(env, exceptionClazz, "Unable to initialize mutex");

		g_print("pthread_mutex_init fail!");

	} else {

	}
	CustomData *data = g_new0(CustomData, 1);
	SET_CUSTOM_DATA(env, thiz, custom_data_field_id, data);
	GST_DEBUG_CATEGORY_INIT(debug_category, "tutorial-2", 0,
			"Android tutorial 2");
	gst_debug_set_threshold_for_name("tutorial-2", GST_LEVEL_DEBUG);
	GST_DEBUG("Created CustomData at %p", data);
	data->app = (*env)->NewGlobalRef(env, thiz);
	GST_DEBUG("Created GlobalRef for app object at %p", data->app);
	pthread_create(&gst_app_thread, NULL, &app_function, data);
}

/* Quit the main loop, remove the native thread and free resources */
static void gst_native_finalize(JNIEnv* env, jobject thiz) {

	// Destory mutex
	if (0 != pthread_mutex_destroy(&mutex)) {
		// Get the exception class
		jclass exceptionClazz = (*env)->FindClass(env,
				"java/lang/RuntimeException");
		// Throw exception
		(*env)->ThrowNew(env, exceptionClazz, "Unable to destroy mutex");
		g_print("pthread_mutex_destroy fail!");
	}

	CustomData *data = GET_CUSTOM_DATA (env, thiz, custom_data_field_id);
	if (!data)
		return;
	GST_DEBUG("Quitting main loop...");
	g_main_loop_quit(data->main_loop);
	GST_DEBUG("Waiting for thread to finish...");
	pthread_join(gst_app_thread, NULL);
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
		g_print("data is null!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
		return;
	}
	GST_DEBUG("Setting state to PLAYING");
	g_print("data is Setting state to PLAYING!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");

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

	iEnd = 0;
}

/* Set pipeline to PAUSED state */
static void gst_native_pause(JNIEnv* env, jobject thiz) {
	CustomData *data = GET_CUSTOM_DATA (env, thiz, custom_data_field_id);
	if (!data)
		return;
	GST_DEBUG("Setting state to PAUSED");
	gst_element_set_state(data->pipeline, GST_STATE_PAUSED);
}

static void gst_native_test(JNIEnv* env, jobject thiz) {
	/*
	 if (0 != pthread_mutex_lock(&mutex))
	 {
	 // Get the exception class
	 jclass exceptionClazz = (*env)->FindClass(env,
	 "java/lang/RuntimeException");
	 // Throw exception
	 (*env)->ThrowNew(env, exceptionClazz, "Unable to lock mutex");
	 g_print("uupthread_mutex_lock fail!");
	 }
	 else
	 {
	 g_print("uupthread_mutex_lock succ!");
	 }
	 sleep(2);
	 g_print("uuthread finish sleeping 2s");
	 //
	 int res = mkfifo("/storage/sdcard0/fifo9000", S_IRWXO);
	 if (res != 0)
	 {
	 g_print("Error while creating a pipe (return:%d, errno:%d)", res, errno);
	 }
	 // Unlock mutex
	 if (0 != pthread_mutex_unlock(&mutex))
	 {
	 jclass exceptionClazz = (*env)->FindClass(env,
	 "java/lang/RuntimeException");
	 // Throw exception
	 (*env)->ThrowNew(env, exceptionClazz, "Unable to unlock mutex");
	 g_print("uupthread_mutex_unlock fail!");
	 }
	 else
	 {
	 g_print("uupthread_mutex_lock succ!");
	 }


	 g_print("after unlocking, uuthread finish sleeping 10s");
	 */
	/*
	 *将从FIFO收到到数据（字符）转换为大写，并输出到屏幕
	 */


		int ret;
		int fd;
		char buffer;
		int nread;
		int i;
		unsigned char * pbuf = malloc(sizeof(unsigned char) * 1024);

		/*打开FIFO*/
		fd = open("/storage/sdcard0/fifo9000", O_RDONLY);
		if(-1 == fd)
		{
			g_print("open /storage/sdcard0/fifo9000 test error/n");
			return ;
		}
		while(1)
		{
			nread = read(fd, pbuf, 1024);
			if(nread > 0)
			{
				for (i = 0; i < nread; ++i)
				g_print("%X ",pbuf[i]);
			}

			sleep(5);
		}

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
		__android_log_print(ANDROID_LOG_ERROR, "tutorial-2",
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
		(void *) gst_native_pause }, { "nativeTest", "()V",
		(void *) gst_native_test }, { "nativeClassInit", "()Z",
		(void *) gst_native_class_init } };

/* Library initializer */
jint JNI_OnLoad(JavaVM *vm, void *reserved) {
	JNIEnv *env = NULL;

	java_vm = vm;

	if ((*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_4) != JNI_OK) {
		__android_log_print(ANDROID_LOG_ERROR, "tutorial-2",
				"Could not retrieve JNIEnv");
		return 0;
	}
	jclass klass = (*env)->FindClass(env,
			"com/gst_sdk_tutorials/tutorial_2/Tutorial2");

	(*env)->RegisterNatives(env, klass, native_methods,
			G_N_ELEMENTS(native_methods));

	pthread_key_create(&current_jni_env, detach_current_thread);

	return JNI_VERSION_1_4;
}
