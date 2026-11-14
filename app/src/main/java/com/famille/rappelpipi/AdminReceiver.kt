package com.famille.rappelpipi

import android.app.admin.DeviceAdminReceiver

/** Administrateur d'appareil : Android refuse de desinstaller l'app tant que ce role est actif. */
class AdminReceiver : DeviceAdminReceiver()
