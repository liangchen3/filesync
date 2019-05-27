package com.lc.filesync.dofilesync;

import com.lc.filesync.sftp.SFTPUtils;

import java.io.File;

/**
 * @ClassName DoFileSync
 * @Description TODO
 * @Author 梁臣
 * @Date 2019/5/23 20:32
 * @Version 1.0
 **/
public class DoFileSyncClient {
    //1. 文件的新建
    //2. 文件的修改  ---> 删除+新建
    //3. 文件的删除

    public static void uploadFile(File file) {
        try {
            SFTPUtils.getSftpUtil().bacthUploadFileDefault(file.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static void downloadFile(File file) {

    }

    /**
     * 3. 删除文件
     *
     * @param file
     */
    public static void deleteFile(File file) {
        try {


            SFTPUtils.getSftpUtil().deleteFile();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
