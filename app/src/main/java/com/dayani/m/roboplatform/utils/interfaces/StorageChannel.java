package com.dayani.m.roboplatform.utils.interfaces;

import com.dayani.m.roboplatform.managers.MyStorageManager;

public interface StorageChannel extends MessageChannel<MyStorageManager.StorageInfo> {

    String getFullFilePath(int id);
}
