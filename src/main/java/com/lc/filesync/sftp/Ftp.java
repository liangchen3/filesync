package com.lc.filesync.sftp;

import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

public class Ftp {

    //打印log日志
    private static final Log logger = LogFactory.getLog(Ftp.class);

    private static Date last_push_date = null;

    private Session sshSession;

    private ChannelSftp channel;

    private static ThreadLocal<Ftp> sftpLocal = new ThreadLocal<Ftp>();

    private Ftp(String host, int port, String username, String password) throws Exception {
        JSch jsch = new JSch();
        jsch.getSession(username, host, port);
        //根据用户名，密码，端口号获取session
        sshSession = jsch.getSession(username, host, port);
        sshSession.setPassword(password);
        //修改服务器/etc/ssh/sshd_config 中 GSSAPIAuthentication的值yes为no，解决用户不能远程登录
        sshSession.setConfig("userauth.gssapi-with-mic", "no");

        //为session对象设置properties,第一次访问服务器时不用输入yes
        sshSession.setConfig("StrictHostKeyChecking", "no");
        sshSession.connect();
        //获取sftp通道
        channel = (ChannelSftp) sshSession.openChannel("sftp");
        channel.connect();
        logger.info("连接ftp成功!" + sshSession);
    }

    /**
     * 是否已连接
     *
     * @return
     */
    private boolean isConnected() {
        return null != channel && channel.isConnected();
    }

    /**
     * 获取本地线程存储的sftp客户端
     *
     * @return
     * @throws Exception
     */
    public static Ftp getSftpUtil(String host, int port, String username, String password) throws Exception {
        //获取本地线程
        Ftp sftpUtil = sftpLocal.get();
        if (null == sftpUtil || !sftpUtil.isConnected()) {
            //将新连接防止本地线程，实现并发处理
            sftpLocal.set(new Ftp(host, port, username, password));
        }
        return sftpLocal.get();
    }

    /**
     * 释放本地线程存储的sftp客户端
     */
    public static void release() {
        if (null != sftpLocal.get()) {
            sftpLocal.get().closeChannel();
            logger.info("关闭连接" + sftpLocal.get().sshSession);
            sftpLocal.set(null);

        }
    }

    /**
     * 关闭通道
     *
     * @throws Exception
     */
    public void closeChannel() {
        if (null != channel) {
            try {
                channel.disconnect();
            } catch (Exception e) {
                logger.error("关闭SFTP通道发生异常:", e);
            }
        }
        if (null != sshSession) {
            try {
                sshSession.disconnect();
            } catch (Exception e) {
                logger.error("SFTP关闭 session异常:", e);
            }
        }
    }

    /**
     * @param directory  上传ftp的目录(远程主机的目录)
     * @param uploadFile 本地文件目录
     */
    public void upload(String directory, String uploadFile) throws Exception {
        try {        //执行列表展示ls 命令
            channel.ls(directory);        //执行盘符切换cd 命令
            channel.cd(directory);
            List<File> files = getFiles(uploadFile, new ArrayList<File>());
            for (int i = 0; i < files.size(); i++) {
                File file = files.get(i);
                InputStream input = new BufferedInputStream(new FileInputStream(file));
                channel.put(input, file.getName());
                try {
                    if (input != null) input.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    logger.error(file.getName() + "关闭文件时.....异常!" + e.getMessage());
                }
                if (file.exists()) {
//                    boolean b = file.delete();
//                    logger.info(file.getName() + "文件上传完毕!删除标识:" + b);
                    logger.info("[ " + file.getName() + " ] 文件上传完毕!");
                }
            }
        } catch (Exception e) {
            logger.error("【子目录创建中】：", e);
            //创建子目录
            channel.mkdir(directory);
        }

    }


    /**
     * @param directory    下载ftp的目录(远程主机的目录)
     * @param downloadFile 本地文件目录
     */
    public void download(String directory, String downloadFile) throws Exception {
        try {
            //执行列表展示ls 命令，查看目录是否存在
//            channel.ls(directory);
//            //执行盘符切换cd 命令
//            channel.cd(directory);
            channel.get(downloadFile, new FileOutputStream(directory));
            List<File> files = getFiles(directory, new ArrayList<File>());
            for (int i = 0; i < files.size(); i++) {
                File file = files.get(i);
                System.out.println("文件的名字:"+file.getName());
//                InputStream input = new BufferedInputStream(new FileInputStream(file));
//                channel.get(downloadFile, file.getName());

//                try {
//                    if (input != null) input.close();
//                } catch (Exception e) {
//                    e.printStackTrace();
//                    logger.error(file.getName() + "关闭文件时.....异常!" + e.getMessage());
//                }
                if (file.exists()) {
//                    boolean b = file.delete();
//                    logger.info(file.getName() + "文件上传完毕!删除标识:" + b);
                    logger.info("[ " + file.getName() + " ] 文件下载完毕!");
                }
            }
        } catch (Exception e) {
            logger.error("【子目录创建中】：", e);
            //创建子目录
            channel.mkdir(directory);
        }

    }


    //获取文件
    public List<File> getFiles(String realpath, List<File> files) {
        File realFile = new File(realpath);
        if (realFile.isDirectory()) {
            File[] subfiles = realFile.listFiles(new FileFilter() {
                @Override
                public boolean accept(File file) {
                    if (null == last_push_date) {
                        return true;
                    } else {
                        long modifyDate = file.lastModified();
                        return modifyDate > last_push_date.getTime();
                    }
                }
            });
            for (File file : subfiles) {
                if (file.isDirectory()) {
                    getFiles(file.getAbsolutePath(), files);
                } else {
                    files.add(file);
                }
                if (null == last_push_date) {
                    last_push_date = new Date(file.lastModified());
                } else {
                    long modifyDate = file.lastModified();
                    if (modifyDate > last_push_date.getTime()) {
                        last_push_date = new Date(modifyDate);
                    }
                }
            }
        }
        return files;
    }


    public static Ftp getDefaultSftpUtil() {
        Ftp ftp = null;
        try {
            ftp = getSftpUtil("106.13.130.208", 22, "root", "liangchen582461!");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ftp;
    }

    public static void main(String[] args) {

        try {
//            getDefaultSftpUtil().upload("/home", "D:\\temp\\apollo\\dabaojian");
            getDefaultSftpUtil().download("/home", "D:\\temp\\apollo\\dabaojian");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}