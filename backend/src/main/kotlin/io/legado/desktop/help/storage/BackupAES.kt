package io.legado.desktop.help.storage

import cn.hutool.crypto.symmetric.AES
import io.legado.desktop.help.config.LocalConfig
import io.legado.desktop.utils.MD5Utils

class BackupAES : AES(
    MD5Utils.md5Encode(LocalConfig.password ?: "").encodeToByteArray(0, 16)
)
