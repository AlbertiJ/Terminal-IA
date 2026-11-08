package com.example.script

data class ScriptMetadata(
    val name: String,
    val virtualPath: String,
    val isExecutable: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val permissionString: String,
    val restrictedUser: String = "u0_a234 (unprivileged)",
    val lineCount: Int = 0
)
