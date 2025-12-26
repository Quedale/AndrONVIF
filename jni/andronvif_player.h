#ifndef ANDRONVIF_ANDRONVIF_PLAYER_H
#define ANDRONVIF_ANDRONVIF_PLAYER_H

#include <glib-object.h>
#include <jni.h>

G_BEGIN_DECLS

#define ANDRONVIF_TYPE_PLAYER AndrONVIFPlayer__get_type()
G_DECLARE_FINAL_TYPE (AndrONVIFPlayer, AndrONVIFPlayer_, ANDRONVIF, PLAYER, GObject)

struct _AndrONVIFPlayer {
    GObject parent_instance;
};


struct _AndrONVIFPlayerClass {
    GObjectClass parent_class;
};

void AndrONVIFPlayerNew__register_jni_class(JNIEnv *env);

#endif //ANDRONVIF_ANDRONVIF_PLAYER_H
