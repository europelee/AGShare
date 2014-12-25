#include <string.h>
#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <gst/gst.h>
#include <pthread.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include<sys/types.h>
#include <sys/mman.h>

#include <gst/video/video.h>
#include <gst/video/videooverlay.h>
#include <sys/time.h>
#include <time.h>

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

#define EN_MUTEX 1

#define SNAP_CAPS "video/x-raw,format=RGB,width=%d,pixel-aspect-ratio=1/1"

static unsigned long CHUNK_SIZE = 1024 * 256; /* Amount of bytes we are sending in each buffer */

/* Structure to contain all our information, so we can pass it to callbacks */
typedef struct _CustomData {
	jobject app; /* Application instance, used to call its methods. A global reference is kept. */
	GstElement *pipeline; /* The running pipeline */
	GMainContext *context; /* GLib context used to run the main loop */
	GMainLoop *main_loop; /* GLib main loop */
	gboolean initialized; /* To avoid informing the UI multiple times about the initialization */
	GstElement *app_source;
	ANativeWindow *native_window; /* The Android native window where video will be rendered */
	GstState target_state;
	//ADD for app layer getting media info
	//refer http://docs.gstreamer.com/display/GstSDK/Playback+tutorial+2%3A+Subtitle+management
	gint n_video; /* Number of embedded video streams */
	gint n_audio; /* Number of embedded audio streams */
	gint n_text; /* Number of embedded subtitle streams */
	gint current_video; /* Currently playing video stream */
	gint current_audio; /* Currently playing audio stream */
	gint current_text; /* Currently playing subtitle stream */
} CustomData;

/* playbin2 flags */
typedef enum {
	GST_PLAY_FLAG_VIDEO = (1 << 0),
	GST_PLAY_FLAG_AUDIO = (1 << 1),
	GST_PLAY_FLAG_TEXT = (1 << 2),
	GST_PLAY_FLAG_VIS = (1 << 3),
	GST_PLAY_FLAG_SOFT_VOLUME = (1 << 4),
	GST_PLAY_FLAG_NATIVE_AUDIO = (1 << 5),
	GST_PLAY_FLAG_NATIVE_VIDEO = (1 << 6),
	GST_PLAY_FLAG_DOWNLOAD = (1 << 7),
	GST_PLAY_FLAG_BUFFERING = (1 << 8),
	GST_PLAY_FLAG_DEINTERLACE = (1 << 9),
	GST_PLAY_FLAG_SOFT_COLORBALANCE = (1 << 10)
} GstPlayFlags;

/* These global variables cache values which are not changing during execution */
static pthread_t gst_app_thread;
static pthread_key_t current_jni_env;
static JavaVM *java_vm;
static jfieldID custom_data_field_id;
static jmethodID set_message_method_id;
static jmethodID on_gstreamer_initialized_method_id;

/*below*/
static int iEnd = 1;
static const char * TAGSTR = "gsutil";

static char SHM_FILE[PATH_MAX] = { 0 };
static char SDCARD_PREFIX[PATH_MAX] = { 0 };

static unsigned char *head_maped = NULL;

//mean max file length 4G
static unsigned long recv_len = 0;

static unsigned long in_index = 0; //next input index
static unsigned long out_index = 0;

// Mutex instance
static pthread_mutex_t * pmutex = NULL;
static pthread_cond_t cont_prebuff;
static pthread_cond_t cont_pushbuff;

static int iMediaType = 0; //0: audio, 1: video
static int iPreBuff = 0; // 0-100
static unsigned long nBuff = 0;
static gboolean hunstatus = FALSE;

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

/**
 * copy from test/Read-metadata.c
 */
static void print_tag(const GstTagList * list, const gchar * tag,
		gpointer unused) {
	gint i, count;
	g_print("tag:%s\n", tag);

	count = gst_tag_list_get_tag_size(list, tag);

	for (i = 0; i < count; i++) {
		gchar *str;

		if (gst_tag_get_type(tag) == G_TYPE_STRING) {
			if (!gst_tag_list_get_string_index(list, tag, i, &str))
				g_assert_not_reached();
		} else {
			str = g_strdup_value_contents(
					gst_tag_list_get_value_index(list, tag, i));
		}

		if (i == 0) {
			g_print("  %15s: %s\n", gst_tag_get_nick(tag), str);
		} else {
			g_print("                 : %s\n", str);
		}

		g_free(str);
	}
}

/* Extract some metadata from the streams */
static void analyze_streams(GstBus *bus, GstMessage *msg, CustomData *data) {
	gint i;
	GstTagList *tags;
	gchar *str;
	guint rate; /* Read some properties */
	g_object_get(data->pipeline, "n-video", &data->n_video, NULL);
	g_object_get(data->pipeline, "n-audio", &data->n_audio, NULL);
	g_object_get(data->pipeline, "n-text", &data->n_text, NULL);
	g_print("%d video stream(s), %d audio stream(s), %d text stream(s)\n",
			data->n_video, data->n_audio, data->n_text);

	for (i = 0; i < data->n_video; i++) {
		tags = NULL; /* Retrieve the stream's video tags */
		g_signal_emit_by_name(data->pipeline, "get-video-tags", i, &tags);
		if (tags) {

			g_print("video stream %d:\n", i);

			gst_tag_list_foreach(tags, print_tag, NULL);
			gst_tag_list_free(tags);
		}
	}

	for (i = 0; i < data->n_audio; i++) {
		tags = NULL; /* Retrieve the stream's audio tags */
		g_signal_emit_by_name(data->pipeline, "get-audio-tags", i, &tags);
		if (tags) {

			g_print("audio stream %d:\n", i);

			gst_tag_list_foreach(tags, print_tag, NULL);
			gst_tag_list_free(tags);

		}
	}

	for (i = 0; i < data->n_text; i++) {
		tags = NULL; /* Retrieve the stream's subtitle tags */
		g_print("subtitle stream %d:\n", i);
		g_signal_emit_by_name(data->pipeline, "get-text-tags", i, &tags);
		if (tags) {

			gst_tag_list_foreach(tags, print_tag, NULL);
			gst_tag_list_free(tags);

		} else {
			g_print("  no tags found\n");
		}
	}
	g_object_get(data->pipeline, "current-video", &data->current_video, NULL);
	g_object_get(data->pipeline, "current-audio", &data->current_audio, NULL);
	g_object_get(data->pipeline, "current-text", &data->current_text, NULL);

	g_print(
			"Currently playing video stream %d, audio stream %d and subtitle stream %d\n",
			data->current_video, data->current_audio, data->current_text);

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
						"get small chunk  data  timeout!");
				set_ui_message("get small chunk  data  timeout", data);
				return FALSE;
			} else {
				continue;
			}
		} //WHILE
	} else {
		int nLoop = 60;
		while (1) {

			unsigned long in_indexcp = in_index;

			if (out_index + CHUNK_SIZE > in_indexcp) {

				__android_log_print(ANDROID_LOG_INFO, TAGSTR,
						"buff readable is empty!");
				hunstatus = TRUE;
#if EN_MUTEX
				if (NULL != pmutex) {
					if (0 != pthread_mutex_lock(pmutex)) {
						g_print("uupthread_mutex_lock fail!");
					} else {
						g_print("uupthread_mutex_lock succ!");
					}
				} else {
					g_print("pmutex is null!");
				}
#endif
				//wait
				pthread_cond_wait(&cont_pushbuff, pmutex);

#if EN_MUTEX
				if (NULL != pmutex) {
					if (0 != pthread_mutex_unlock(pmutex)) {
						g_print("uupthread_mutex_UNlock fail!");
					} else {
						g_print("uupthread_mutex_UNlock succ!");
					}
				} else {
					g_print("pmutex is null!");
				}
#endif
				nLoop--;
				if (nLoop <= 0) {
					__android_log_print(ANDROID_LOG_ERROR, TAGSTR,
							"get CHUNK_SIZE data  timeout!");
					set_ui_message("get CHUNK_SIZE data  timeout", data);
					//gst_buffer_unref(buffer);

					return FALSE;
				} else {
					continue;
				}
				//return FALSE;
			} else {

				__android_log_print(ANDROID_LOG_ERROR, TAGSTR,
						"spend %d s for getting CHUNK_SIZE data", (60 - nLoop));
				hunstatus = FALSE;
				break;
			}

		} //while

		nRead = CHUNK_SIZE;

	}

	if (100 == iPreBuff) {
		nRead = recv_len;
		if (madvise(head_maped + out_index, nRead, MADV_SEQUENTIAL) < 0) {
			g_print("warning: madvise failed: %s", g_strerror(errno));

		}
	}

	//buffer = gst_buffer_new();
	//replace with gst_buffer_new_wrapped_full when gstreamer 1.x
	buffer = gst_buffer_new_wrapped_full(GST_MEMORY_FLAG_READONLY,
			head_maped + out_index, nRead, 0, nRead, NULL, NULL);

#if 0
	GST_BUFFER_DATA (buffer) = head_maped + out_index;
	GST_BUFFER_SIZE (buffer) = nRead;
#endif

	GST_DEBUG("gst_buffer_get_size %d", gst_buffer_get_size(buffer));

	out_index += nRead; //CHUNK_SIZE

	if (out_index == recv_len) {
		__android_log_print(ANDROID_LOG_INFO, TAGSTR, "finish reading");

		iEnd = 1;
	}

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

	//gstreamer 0.10.36 is ok, but 1.x gst_caps_new_any has bug
	//test with gst_caps_is_any.
	//already file bug https://bugzilla.gnome.org/show_bug.cgi?id=731704
	//any means any
#if 0
	GstCaps * gany;
	gany = gst_caps_new_any();
	g_object_set(source, "caps", gany, NULL);
	gst_caps_unref(gany);
#endif
	/* we can set the length in appsrc. This allows some elements to estimate the
	 * total duration of the stream. It's a good idea to set the property when you
	 * can but it's not required.
	 */
	g_object_set(source, "size", (gint64) recv_len, NULL);
	g_print("source size is %d", gst_app_src_get_size());

	if (100 != iPreBuff) {
		g_object_set(source, "max-bytes", (gint64)(4 * CHUNK_SIZE), NULL);
		g_print("max-bytes: %d", gst_app_src_get_max_bytes(source));

		g_object_set(source, "min-percent", 60, NULL);
	} else {
		g_object_set(source, "max-bytes", (gint64) 0, NULL);
		g_print("max-bytes: %d", gst_app_src_get_max_bytes(source));
	}

	g_signal_connect(source, "need-data", G_CALLBACK(start_feed), data);
	g_signal_connect(source, "enough-data", G_CALLBACK(stop_feed), data);

}

static void buffering_cb(GstBus *bus, GstMessage *msg, CustomData *data) {
#if 0
	static int itest = 0;
	static int b0 = 0, b1 = 0, b2 = 0, b3 = 0;

	if (0 == itest)
	{

		GstElement *vbin = gst_bin_get_by_name(GST_BIN(data->pipeline), "vbin");
		if (NULL != vbin && b0 == 0)
		{
			g_print("vbin is not null");
			b0 = 1;
			gst_object_unref(vbin);

		}
		GstElement *vqueue = gst_bin_get_by_name(GST_BIN(data->pipeline), "vqueue");
		if (NULL != vqueue && 0 == b1)
		{
			g_print("vqueue is not null");

			b1 =1;

			g_object_set(vqueue, "max-size-buffers", 8, NULL);
			guint tmp;
			g_object_get(vqueue, "max-size-buffers", &tmp, NULL);
			g_print(" vqueue max-size-buffers %u ", tmp);

			g_object_set(vqueue, "max-size-bytes", 1024*1024*8, NULL);
			g_object_get(vqueue, "max-size-bytes", &tmp, NULL);
			g_print(" vqueue max-size-bytes %u ", tmp);

			gst_object_unref(vqueue);
		}

		GstElement *multiqueue = gst_bin_get_by_name(GST_BIN(data->pipeline), "multiqueue0");
		if (NULL != multiqueue && 0 == b2)
		{
			g_print("multiqueue is not null");

			b2 = 1;

			g_object_set(multiqueue, "use-buffering", (gboolean)TRUE, NULL);

			g_object_set(multiqueue, "max-size-buffers", 8, NULL);
			guint tmp;
			g_object_get(multiqueue, "max-size-buffers", &tmp, NULL);
			g_print("max-size-buffers %u ", tmp);

			g_object_set(multiqueue, "max-size-bytes", 1024*1024*8, NULL);
			g_object_get(multiqueue, "max-size-bytes", &tmp, NULL);
			g_print("max-size-bytes %u ", tmp);

			gst_object_unref(multiqueue);

		}

		GstElement *decodebin = gst_bin_get_by_name(GST_BIN(data->pipeline), "decodebin0");
		if (NULL != decodebin && 0 == b3)
		{
			g_print("decodebin is not null");

			b3 = 1;

			g_object_set(decodebin, "use-buffering", (gboolean)TRUE, NULL);
			g_object_set(decodebin, "low-percent", 10, NULL);
			g_object_set(decodebin, "high-percent", 99, NULL);
			g_object_set(decodebin, "max-size-buffers", 8, NULL);
			guint tmp;
			g_object_get(decodebin, "max-size-buffers", &tmp, NULL);
			g_print("decodebin max-size-buffers %u ", tmp);

			g_object_set(decodebin, "max-size-bytes", 1024*1024*64, NULL);
			g_object_get(decodebin, "max-size-bytes", &tmp, NULL);
			g_print("decodebin max-size-bytes %u ", tmp);

			g_object_set(decodebin, "max-size-time", (guint64)4000000000, NULL);

			gst_object_unref(decodebin);

		}

		if (1 == b0 && 1 == b1 && 1 == b2 && 1 == b3)
		itest = 1;
	}
#endif
	gint percent;

	//g_print("buff_cb: target_state %d(play:%d)", data->target_state,
	//		GST_STATE_PLAYING);
	gst_message_parse_buffering(msg, &percent);
	g_print("percent:%d", percent);
	if (percent < 100 && data->target_state >= GST_STATE_PAUSED) {
		gchar * message_string = g_strdup_printf("Buffering %d%%", percent);
		gst_element_set_state(data->pipeline, GST_STATE_PAUSED);

		set_ui_message(message_string, data);
		g_free(message_string);
	} else if (data->target_state >= GST_STATE_PLAYING) {
		gst_element_set_state(data->pipeline, GST_STATE_PLAYING);
	} else if (data->target_state >= GST_STATE_PAUSED) {
		set_ui_message("Buffering complete", data);
	}
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
	data->target_state = GST_STATE_NULL;
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

		gchar *message = g_strdup_printf("State changed to %s from %s",
				gst_element_state_get_name(new_state),
				gst_element_state_get_name(old_state));
		set_ui_message(message, data);
		g_free(message);
	} else {
		g_print("msg from %s, state %s changed to %s\n",
				GST_OBJECT_NAME(GST_MESSAGE_SRC(msg)),
				gst_element_state_get_name(old_state),
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

		if (1 == iMediaType && data->native_window) {
			g_print("set draw window for pipeline");
			/* The main loop is running and we received a native window, inform the sink about it */
			gst_video_overlay_set_window_handle(
					GST_VIDEO_OVERLAY(data->pipeline),
					(guintptr) data->native_window);

		}
		(*env)->CallVoidMethod(env, data->app,
				on_gstreamer_initialized_method_id);
		if ((*env)->ExceptionCheck(env)) {
			GST_ERROR("Failed to call Java method");
			(*env)->ExceptionClear(env);
		}
		data->initialized = TRUE;
	}
}

static void clock_lost_cb(GstBus *bus, GstMessage *msg, CustomData *data) {
	if (data->target_state >= GST_STATE_PLAYING) {
		gst_element_set_state(data->pipeline, GST_STATE_PAUSED);
		gst_element_set_state(data->pipeline, GST_STATE_PLAYING);
	}
}

static void eos_cb(GstBus *bus, GstMessage *msg, CustomData *data) {

	g_print("eos_cb called!");

	data->initialized = FALSE;
	gst_element_set_state(data->pipeline, GST_STATE_PAUSED);
	g_main_loop_quit(data->main_loop);

}

static void clearFlags() {
	__android_log_print(ANDROID_LOG_INFO, TAGSTR, "clearFlags");
	recv_len = 0;
	in_index = 0;
	out_index = 0;
	hunstatus = FALSE;

}

/* Main method for the native code. This is executed on its own thread. */
static void *app_function(void *userdata) {

	gst_debug_set_default_threshold(GST_LEVEL_DEBUG);
	__android_log_print(ANDROID_LOG_INFO, TAGSTR, "app_function start");

	//in_index = 0;
	out_index = 0;

	guint flags;

	while (1) {
		g_print("buff some content before playing!");

		if (1 == iEnd) {
			g_print("unexception error!");
			break;
		}

#if EN_MUTEX
		if (NULL != pmutex) {
			if (0 != pthread_mutex_lock(pmutex)) {
				g_print("uupthread_mutex_lock fail!");
			} else {
				g_print("uupthread_mutex_lock succ!");
			}
		} else {
			g_print("pmutex is null!");
		}
#endif
		unsigned long in_indexcp = in_index;
		if (in_indexcp >= nBuff) {
			//break;
		} else {
			//sleep(1);
			if (NULL != pmutex)
				pthread_cond_wait(&cont_prebuff, pmutex);
		}

		//avoid inject function always calling cont-signal
		nBuff = 0;
#if EN_MUTEX
		if (NULL != pmutex) {
			if (0 != pthread_mutex_unlock(pmutex)) {
				g_print("uupthread_mutex_UNlock fail!");
			} else {
				g_print("uupthread_mutex_UNlock succ!");
			}
		} else {
			g_print("pmutex is null!");
		}
#endif

		break;
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
	//data->pipeline = gst_parse_launch("playbin uri=appsrc://", &error);
	data->pipeline = gst_parse_launch("playbin", &error);
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

	/* Disable subtitles */
	g_object_get(data->pipeline, "flags", &flags, NULL);
	flags |= GST_PLAY_FLAG_VIDEO | GST_PLAY_FLAG_AUDIO
			| GST_PLAY_FLAG_BUFFERING;
	//| GST_PLAY_FLAG_DOWNLOAD;
	flags &= ~GST_PLAY_FLAG_TEXT;
	g_object_set(data->pipeline, "flags", flags, NULL);

	g_object_set(data->pipeline, "buffer-duration", G_MAXUINT, NULL);
	gint64 tmp0;
	g_object_get(data->pipeline, "buffer-duration", &tmp0, NULL);
	g_print("buffer-dur is %lld s", tmp0 / 1000000000);

	g_object_set(data->pipeline, "buffer-size", 1024 * 1024 * 6, NULL);
	guint tmp;
	g_object_get(data->pipeline, "buffer-size", &tmp, NULL);
	g_print("buffer-size is %u Megabytes", tmp / (1024 * 1024));

	data->target_state = GST_STATE_READY;
	gst_element_set_state(data->pipeline, GST_STATE_READY);

	g_object_set(data->pipeline, "uri", "appsrc://", NULL);

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

	g_signal_connect(G_OBJECT(bus), "message::buffering",
			(GCallback) buffering_cb, data);
	g_signal_connect(G_OBJECT(bus), "message::clock-lost",
			(GCallback) clock_lost_cb, data);

	g_signal_connect(G_OBJECT(bus), "message::tag", (GCallback) analyze_streams,
			data);

	gst_object_unref(bus);

	/* Create a GLib Main Loop and set it to run */
	GST_DEBUG("Entering main loop... (CustomData:%p)", data);
	data->main_loop = g_main_loop_new(data->context, FALSE);
	check_initialization_complete(data);

	/* Start playing the pipeline */
	//gst_element_set_state(data->pipeline, GST_STATE_PLAYING);
	GstStateChangeReturn ret;

	data->target_state = GST_STATE_PLAYING;
	ret = gst_element_set_state(data->pipeline, GST_STATE_PLAYING);
	if (ret == GST_STATE_CHANGE_FAILURE) {
		g_print("Unable to set the pipeline to the playing state.\n");
		return;
	} else if (ret == GST_STATE_CHANGE_NO_PREROLL) {
		g_print(" data.is_live = TRUE.\n");

	} else if (ret == GST_STATE_CHANGE_SUCCESS) {
		g_print(" GST_STATE_CHANGE_SUCCESS.\n");
	}

	g_print("ret is %d", ret);

	g_main_loop_run(data->main_loop);

	GST_DEBUG("Exited main loop");
	g_main_loop_unref(data->main_loop);
	data->main_loop = NULL;

	/* Free resources */
	g_main_context_pop_thread_default(data->context);
	g_main_context_unref(data->context);
	data->target_state = GST_STATE_NULL;
	gst_element_set_state(data->pipeline, GST_STATE_NULL);
	gst_object_unref(data->pipeline);

	fin_shmfile();

	clearFlags();

	return NULL;
}



static void getExtPath(JNIEnv* env) {
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

	snprintf(SDCARD_PREFIX, PATH_MAX, "%s", path);

	(*env)->ReleaseStringUTFChars(env, obj_Path, path);
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

	getExtPath(env);
	snprintf(SHM_FILE, PATH_MAX, "%s/media_shm", SDCARD_PREFIX);
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
static void gst_native_set_chunksize(JNIEnv* env, jobject thiz, jint csize) {
	CHUNK_SIZE = csize;
}

static void gst_native_set_prebuff_scale(JNIEnv* env, jobject thiz, jint scale) {
	if (scale < 0)
		scale = 0;
	if (scale > 100)
		scale = 100;

	iPreBuff = scale;

	//need first set recv_len
	nBuff = recv_len * ((float) iPreBuff / 100);
	g_print("nBuff is %lu, recv_len:%lu", nBuff, recv_len);
	//above not acculate, so
	if (nBuff > recv_len)
		nBuff = recv_len;

}

static void gst_native_set_mediatype(JNIEnv* env, jobject thiz, jint mediatype) {
	iMediaType = mediatype;
}

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

	// Initialize mutex
	//it need this thread start first before alljoyn calling input data
#if EN_MUTEX
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
#endif

	pthread_cond_init(&cont_prebuff, NULL);
	pthread_cond_init(&cont_pushbuff, NULL);

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

	clearFlags();

	// Destory mutex
#if EN_MUTEX
	if ((NULL != pmutex) && (0 != pthread_mutex_destroy(pmutex))) {
		g_print("pthread_mutex_destroy fail!");
	} else {
		g_print("pthread_mutex_destroy succ!");
	}

	if (NULL != pmutex) {
		free(pmutex);
		pmutex = NULL;
	}
#endif

	GST_DEBUG("Deleting GlobalRef for app object at %p", data->app);
	(*env)->DeleteGlobalRef(env, data->app);
	GST_DEBUG("Freeing CustomData at %p", data);
	g_free(data);
	SET_CUSTOM_DATA(env, thiz, custom_data_field_id, NULL);
	GST_DEBUG("Done finalizing");
}


static int checkGstAppThreadlive() {

	int ret = 0;
	int pthread_kill_err;
	pthread_kill_err = pthread_kill(gst_app_thread, 0);

	if (pthread_kill_err == ESRCH)
	{
		__android_log_print(ANDROID_LOG_INFO, TAGSTR, "threadid %u not exist", (unsigned int) gst_app_thread);
		ret = 0;
	}
	else if (pthread_kill_err == EINVAL)
	{
		__android_log_print(ANDROID_LOG_INFO, TAGSTR, "invalid signal");
		ret = 0;
	}
	else
	{
		__android_log_print(ANDROID_LOG_INFO, TAGSTR, "threadid %u still live", (unsigned int) gst_app_thread);
		ret = 1;
	}

	return ret;
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
	g_print("iEnd is %d\n", iEnd);
	if (iEnd == 1) {

		iEnd = 0;

		//processing unexception
		{
			fin_shmfile();
			gboolean ans = init_shmfile(env);
			if (FALSE == ans) {
				iEnd = 1;
				return;
			}
		}

	}

	if (0 == checkGstAppThreadlive())
	pthread_create(&gst_app_thread, NULL, &app_function, data);
}

/* Set pipeline to PAUSED state */
static void gst_native_pause(JNIEnv* env, jobject thiz) {
	CustomData *data = GET_CUSTOM_DATA (env, thiz, custom_data_field_id);
	if (!data)
		return;
	GST_DEBUG("Setting state to PAUSED");
	data->target_state = GST_STATE_PAUSED;
	gst_element_set_state(data->pipeline, GST_STATE_PAUSED);
}

static void gst_native_inputdata(JNIEnv* env, jobject thiz, jbyteArray jbarray) {

	if (0 >= recv_len) {

		return;
	}

	if (NULL == head_maped) {

		gboolean ans = init_shmfile(env);
		if (FALSE == ans) {
			iEnd = 1;

			__android_log_print(ANDROID_LOG_ERROR, TAGSTR,
					"init_shmfile fail");

			return;
		}

		if (NULL == head_maped)
		{
			iEnd = 1;
			__android_log_print(ANDROID_LOG_ERROR, TAGSTR,
					"could not inputdata, head_maped null");
			return;
		}

		//for case:
		//recv data before opening mmplayer
		//else cause fin_shmfile when play trrigered!
		g_print("iEnd is %d\n", iEnd);
		iEnd = 0;

		in_index = 0; //not in app_function!
	}

	int nArrLen = (*env)->GetArrayLength(env, jbarray);
	unsigned char *chArr = (unsigned char *) ((*env)->GetByteArrayElements(env,
			jbarray, 0));

	struct timeval t_start, t_end;

#if ( defined(NDK_DEBUG) && ( NDK_DEBUG == 1 ) )
	//get start time
	gettimeofday(&t_start, NULL);
	long begin = ((long) t_start.tv_sec) * 1000 + (long) t_start.tv_usec / 1000;

	__android_log_print(ANDROID_LOG_INFO, TAGSTR, "begin time is  %ld", begin);
#endif
	//care number overflow!
	if (in_index + nArrLen <= recv_len)
		memcpy(head_maped + in_index, chArr, nArrLen);
	else {
		__android_log_print(ANDROID_LOG_ERROR, TAGSTR,
				"in_index+nArrLen %ld > recv_len %ld", in_index + nArrLen,
				recv_len);
		(*env)->ReleaseByteArrayElements(env, jbarray, chArr, 0);
		return;
	}

	in_index += nArrLen;

	if (nBuff > 0 && in_index >= nBuff) {
#if EN_MUTEX
		if (NULL != pmutex) {
			if (0 != pthread_mutex_lock(pmutex)) {
				g_print("uupthread_mutex_lock fail!");
			} else {
				g_print("uupthread_mutex_lock succ!");
			}
		} else {
			g_print("pmutex is null!");
		}
#endif
		g_print("trigger app_function continuing!");
		if (NULL != pmutex)
			pthread_cond_signal(&cont_prebuff);

#if EN_MUTEX
		if (NULL != pmutex) {
			if (0 != pthread_mutex_unlock(pmutex)) {
				g_print("uupthread_mutex_UNlock fail!");
			} else {
				g_print("uupthread_mutex_UNlock succ!");
			}
		} else {
			g_print("pmutex is null!");
		}
#endif
	}

	if (TRUE == hunstatus && out_index + CHUNK_SIZE <= in_index) {
#if EN_MUTEX
		if (NULL != pmutex) {
			if (0 != pthread_mutex_lock(pmutex)) {
				g_print("uupthread_mutex_lock fail!");
			} else {
				g_print("uupthread_mutex_lock succ!");
			}
		} else {
			g_print("pmutex is null!");
		}
#endif
		g_print("trigger push_data");
		pthread_cond_signal(&cont_pushbuff);

#if EN_MUTEX
		if (NULL != pmutex) {
			if (0 != pthread_mutex_unlock(pmutex)) {
				g_print("uupthread_mutex_UNlock fail!");
			} else {
				g_print("uupthread_mutex_UNlock succ!");
			}
		} else {
			g_print("pmutex is null!");
		}
#endif
	}

#if ( defined(NDK_DEBUG) && ( NDK_DEBUG == 1 ) )
	gettimeofday(&t_end, NULL);
	long end = ((long) t_end.tv_sec) * 1000 + (long) t_end.tv_usec / 1000;

	__android_log_print(ANDROID_LOG_INFO, TAGSTR, "end time is  %ld", end);

	__android_log_print(ANDROID_LOG_INFO, TAGSTR, "end-begin is  %ld",
			(long) end - (long) begin);
#endif
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

	return TRUE;

}

/**
 *copy from gstreamer baseplugin tests/examples/snapshot
 *for local videofile
 */
static void gst_native_snapshot(JNIEnv* env, jobject thiz, jstring filePath,
		jint width) {
	GstElement *pipeline = NULL, *sink = NULL;
	gint height;
	GstSample *sample = NULL;
	gchar *descr = NULL;
	GError *error = NULL;
	//GdkPixbuf *pixbuf = NULL;
	gint64 duration, position;
	GstStateChangeReturn ret;
	gboolean res;
	GstMapInfo map;

	CustomData *data = GET_CUSTOM_DATA (env, thiz, custom_data_field_id);
	if (!data)
		return;

	char * ptrVideoFile = NULL;
	ptrVideoFile = (char*) (*env)->GetStringUTFChars(env, filePath, NULL);
	g_print("snapshot: %s", ptrVideoFile);
	char * pch = NULL;
	pch = strrchr(ptrVideoFile, '/');
	if (!pch) {
		goto SNAP_ERROR;
	}
	pch++;
	if (!pch) {
		goto SNAP_ERROR;
	}

	char fileName[128] = {0};
	strncpy(fileName, pch, strlen(pch)+1);
	fileName[127] = 0; //prevent pch too long

	/* create a new pipeline */
	descr = g_strdup_printf(
			"uridecodebin uri=file://%s ! videoconvert ! videoscale ! "
					" appsink name=sink caps=\"" SNAP_CAPS "\"", ptrVideoFile,
			width);

	g_print("%s", descr);
	set_ui_message(descr, data);
	pipeline = gst_parse_launch(descr, &error);

	if (error != NULL) {
		g_print("could not construct pipeline: %s\n", error->message);
		g_error_free(error);
		set_ui_message(error->message, data);
		goto SNAP_ERROR;
	}

	/* get sink */
	sink = gst_bin_get_by_name(GST_BIN(pipeline), "sink");

	/* set to PAUSED to make the first frame arrive in the sink */
	ret = gst_element_set_state(pipeline, GST_STATE_PAUSED);
	switch (ret) {
	case GST_STATE_CHANGE_FAILURE:
		g_print("failed to play the file\n");
		set_ui_message("failed to play the file1", data);
		goto SNAP_ERROR;
	case GST_STATE_CHANGE_NO_PREROLL:
		/* for live sources, we need to set the pipeline to PLAYING before we can
		 * receive a buffer. We don't do that yet */
		g_print("live sources not supported yet\n");
		set_ui_message("live sources not supported yet", data);
		goto SNAP_ERROR;
	default:
		break;
	}
	/* This can block for up to 5 seconds. If your machine is really overloaded,
	 * it might time out before the pipeline prerolled and we generate an error. A
	 * better way is to run a mainloop and catch errors there. */

	ret = gst_element_get_state(pipeline, NULL, NULL, 5 * GST_SECOND);
	if (ret == GST_STATE_CHANGE_FAILURE) {
		g_print("failed to play the file2\n");
		set_ui_message("failed to play the file2", data);
		goto SNAP_ERROR;
	}

	/* get the duration */
	gst_element_query_duration(pipeline, GST_FORMAT_TIME, &duration);

	if (duration != -1)
		/* we have a duration, seek to 50% */
		position = duration * 50 / 100;
	else
		/* no duration, seek to 1 second, this could EOS */
		position = 1 * GST_SECOND;

	//set_ui_message("gst_element_query_duration", data);

	/* seek to the a position in the file. Most files have a black first frame so
	 * by seeking to somewhere else we have a bigger chance of getting something
	 * more interesting. An optimisation would be to detect black images and then
	 * seek a little more */
	gst_element_seek_simple(pipeline, GST_FORMAT_TIME,
			GST_SEEK_FLAG_KEY_UNIT | GST_SEEK_FLAG_FLUSH, position);

	//set_ui_message("gst_element_seek_simple", data);

	/* get the preroll buffer from appsink, this block untils appsink really
	 * prerolls */
	g_signal_emit_by_name(sink, "pull-preroll", &sample, NULL);

	/* if we have a buffer now, convert it to a pixbuf. It's possible that we
	 * don't have a buffer because we went EOS right away or had an error. */
	if (sample) {
		GstBuffer *buffer;
		GstCaps *caps;
		GstStructure *s;

		/* get the snapshot buffer format now. We set the caps on the appsink so
		 * that it can only be an rgb buffer. The only thing we have not specified
		 * on the caps is the height, which is dependant on the pixel-aspect-ratio
		 * of the source material */
		caps = gst_sample_get_caps(sample);
		if (!caps) {
			g_print("could not get snapshot format\n");
			set_ui_message("could not get snapshot format", data);
			goto SNAP_ERROR;
		}
		s = gst_caps_get_structure(caps, 0);

		/* we need to get the final caps on the buffer to get the size */
		res = gst_structure_get_int(s, "width", &width);
		res |= gst_structure_get_int(s, "height", &height);
		if (!res) {
			g_print("could not get snapshot dimension\n");
			set_ui_message("could not get snapshot dimension", data);
			goto SNAP_ERROR;
		}

		/* create pixmap from buffer and save, gstreamer video buffers have a stride
		 * that is rounded up to the nearest multiple of 4 */
		buffer = gst_sample_get_buffer(sample);
		gst_buffer_map(buffer, &map, GST_MAP_READ);
		//pixbuf = gdk_pixbuf_new_from_data(map.data, GDK_COLORSPACE_RGB, FALSE,
		//		8, width, height, GST_ROUND_UP_4(width * 3), NULL, NULL);

		getExtPath(env);
		//todo
		char savePath[PATH_MAX] = { 0 };
		snprintf(savePath, PATH_MAX, "%s/%s.png", SDCARD_PREFIX, fileName);
		savePath[PATH_MAX-1] = 0;

		/* save the pixbuf */
		//gdk_pixbuf_save(pixbuf, savePath, "png",
		//		&error, NULL);
		gst_buffer_unmap(buffer, &map);
	} else {
		g_print("could not make snapshot\n");
		set_ui_message("sample is null, could not make snapshot", data);
	}

	SNAP_ERROR:
	/* cleanup and exit */
	if (NULL != ptrVideoFile)
		(*env)->ReleaseStringUTFChars(env, filePath, ptrVideoFile);

	if (NULL != descr)
		g_free(descr);

	if (NULL != pipeline) {
		gst_element_set_state(pipeline, GST_STATE_NULL);
		gst_object_unref(pipeline);
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
		__android_log_print(ANDROID_LOG_ERROR, TAGSTR,
				"The calling class does not implement all necessary interface methods");
		return JNI_FALSE;
	}
	return JNI_TRUE;
}

static void gst_native_surface_init(JNIEnv *env, jobject thiz, jobject surface) {

	g_print("gst_native_surface_init start");
	if (1 != iMediaType) {
		return;
	}

	CustomData *data = GET_CUSTOM_DATA (env, thiz, custom_data_field_id);
	if (!data)
		return;

	ANativeWindow *new_native_window = ANativeWindow_fromSurface(env, surface);
	g_print("Received surface %p (native window %p)", surface,
			new_native_window);

	if (data->native_window) {
		ANativeWindow_release(data->native_window);
		if (data->native_window == new_native_window) {
			g_print("New native window is the same as the previous one");
			if (data->pipeline) {
				gst_video_overlay_expose(GST_VIDEO_OVERLAY(data->pipeline));
				gst_video_overlay_expose(GST_VIDEO_OVERLAY(data->pipeline));
			}
			return;
		} else {
			g_print("Released previous native window %p", data->native_window);
			data->initialized = FALSE;
		}
	}
	data->native_window = new_native_window;

	check_initialization_complete(data);
}

static void gst_native_surface_finalize(JNIEnv *env, jobject thiz) {

	g_print("gst_native_surface_finalize start");
	if (1 != iMediaType) {
		return;
	}

	CustomData *data = GET_CUSTOM_DATA (env, thiz, custom_data_field_id);
	if (!data)
		return;

	if (NULL == data->native_window) {
		return;
	}

	g_print("Releasing Native Window %p", data->native_window);

	if (data->pipeline) {
		gst_video_overlay_set_window_handle(GST_VIDEO_OVERLAY(data->pipeline),
				(guintptr) NULL);
		gst_element_set_state(data->pipeline, GST_STATE_READY);
	}

	ANativeWindow_release(data->native_window);
	data->native_window = NULL;
	data->initialized = FALSE;
}

/* List of implemented native methods */
static JNINativeMethod native_methods[] = { { "nativeInit", "()V",
		(void *) gst_native_init }, { "nativeFinalize", "()V",
		(void *) gst_native_finalize }, { "nativePlay", "()V",
		(void *) gst_native_play }, { "nativeSnapshot",
		"(Ljava/lang/String;I)V", (void *) gst_native_snapshot }, {
		"nativePause", "()V", (void *) gst_native_pause }, { "nativeInputData",
		"([B)V", (void *) gst_native_inputdata }, { "nativeSetRecvLen", "(J)Z",
		(void *) gst_native_setrecvlen }, { "nativeSetChunkSize", "(I)V",
		(void *) gst_native_set_chunksize }, { "nativeSetMediaType", "(I)V",
		(void *) gst_native_set_mediatype }, { "nativeSetBuffScale", "(I)V",
		(void *) gst_native_set_prebuff_scale }, { "nativeSurfaceInit",
		"(Ljava/lang/Object;)V", (void *) gst_native_surface_init }, {
		"nativeSurfaceFinalize", "()V", (void *) gst_native_surface_finalize },
		{ "nativeClassInit", "()Z", (void *) gst_native_class_init } };

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
