package org.openscanner.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdTime: Long = System.currentTimeMillis(),
    val color: String = "#EEAA3B",
    val orderIndex: Int = 0
)

data class FolderWithCount(
    val folder: Folder,
    val docCount: Int
)
