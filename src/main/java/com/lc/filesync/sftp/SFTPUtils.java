package com.lc.filesync.sftp;

import com.jcraft.jsch.*;
import com.lc.filesync.monitor.FileListenerBootStarp;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

/**
 * sftp工具类
 */
@Slf4j
public class SFTPUtils {
    private static ChannelSftp sftp = null;
    private static Session sshSession = null;
    private static Integer i = 0;
    private static List<String> sftpList = new ArrayList<>();
    /**
     * 将对象放置本地线程，防止并发
     */
    private static ThreadLocal<SFTPUtils> sftpLocal = new ThreadLocal<SFTPUtils>();

    /**
     * 获取本地线程存储的sftp客户端
     *
     * @return
     * @throws Exception
     */
    public static SFTPUtils getSftpUtil() throws Exception {
        //获取本地线程
        SFTPUtils sftpUtil = sftpLocal.get();
        if (null == sftpUtil || !sftpUtil.isConnected()) {
            //将新连接防止本地线程，实现并发处理
            sftpLocal.set(new SFTPUtils());
        }
        return sftpLocal.get();
    }

    public SFTPUtils() {
        try {
            connectDefaultMachine();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 是否已连接
     *
     * @return
     */
    private boolean isConnected() {
        return null != sftp && sftp.isConnected();
    }

    /**
     * 通过SFTP连接服务器
     */
    public static void connect(String ip, String username, String password, Integer port) throws Exception {

        JSch jsch = new JSch();
        try {
            if (port <= 0) {
                // 连接服务器，采用默认端口
                sshSession = jsch.getSession(username, ip);
            } else {
                // 采用指定的端口连接服务器
                sshSession = jsch.getSession(username, ip, port);
            }

            // 如果服务器连接不上，则抛出异常
            if (sshSession == null) {
                throw new Exception("服务器异常");
            }

            // 设置登陆主机的密码
            sshSession.setPassword(password);// 设置密码
            // 设置第一次登陆的时候提示，可选值：(ask | yes | no)
            sshSession.setConfig("StrictHostKeyChecking", "no");
            // 设置登陆超时时间
            sshSession.connect(300000);
            Channel channel = sshSession.openChannel("sftp");
            channel.connect();

            sftp = (ChannelSftp) channel;

        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * 关闭连接
     */
    public static void disconnect() {
        if (sftp != null) {
            if (sftp.isConnected()) {
                sftp.disconnect();
                if (log.isInfoEnabled()) {
                    log.info("已关闭sftp");
                }
            }
        }
        if (sshSession != null) {
            if (sshSession.isConnected()) {
                sshSession.disconnect();
                if (log.isInfoEnabled()) {
                    log.info("已关闭sshSession");
                }
            }
        }
    }

    /**
     * 批量下载文件
     *
     * @param remotePath：远程下载目录(以路径符号结束)
     * @param localPath：本地保存目录(以路径符号结束,D:\Duansha\sftp\)
     * @param fileFormat：下载文件格式(以特定字符开头,为空不做检验)
     * @param fileEndFormat：下载文件格式(文件格式)
     * @param del：下载后是否删除sftp文件
     * @return
     */
    public static List<String> batchDownLoadFile(String remotePath, String localPath,
                                                 String fileFormat, String fileEndFormat, boolean del) throws SftpException {
        //补全 '/' '\'
        if (!localPath.endsWith("\\")) {
            localPath = localPath + "\\";
        }
        if (!remotePath.endsWith("/")) {
            remotePath = remotePath + "/";
        }

        List<String> filenames = new ArrayList<String>();
        try {
            getSftpPathList(remotePath, sftp);
            for (String filePath : sftpList) {
                String localPathTemp = localPath;
                String fileName = filePath.substring(filePath.lastIndexOf("/") + 1);
                String remotePathSub = filePath.substring(0, filePath.lastIndexOf("/") + 1);
//               String middleDir=remotePathSub.substring()
//
                String middleDir = remotePathSub.substring(remotePath.length()).replaceAll("/", "\\\\");
                System.out.println("localPath:" + localPathTemp);
                localPathTemp = localPathTemp + middleDir;
                System.out.println("想要的路径:" + localPathTemp);
                System.out.println("remotePathSub:" + remotePathSub);

                batchDownLoadFileLogic(remotePathSub, localPathTemp, fileFormat, fileEndFormat, del, fileName, filenames);
                System.out.println("文件的绝对路径：" + filePath);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        Vector v = sftp.ls(remotePath);
        if (v.size() > 0) {
            System.out.println("本次处理文件个数不为零,开始下载...fileSize=" + v.size());
            Iterator it = v.iterator();
            while (it.hasNext()) {
                ChannelSftp.LsEntry entry = (ChannelSftp.LsEntry) it.next();
                String filename = entry.getFilename();
                SftpATTRS attrs = entry.getAttrs();
                if (!attrs.isDir()) {
                    filenames = batchDownLoadFileLogic(remotePath, localPath, fileFormat, fileEndFormat, del, filename, filenames);
                } else {
                    System.out.println("是一个文件啊");
                }
            }
        }
        if (log.isInfoEnabled()) {
            log.info("download file is success:remotePath=" + remotePath
                    + "and localPath=" + localPath + ",file size is"
                    + v.size());
        }

        return filenames;
    }

    /**
     * 批量下载远程文件逻辑
     *
     * @param remotePath
     * @param localPath
     * @param fileFormat
     * @param fileEndFormat
     * @param del
     * @param filename
     * @param filenames
     * @return
     */
    private static List<String> batchDownLoadFileLogic(String remotePath, String localPath, String fileFormat, String fileEndFormat, boolean del, String filename, List<String> filenames) {
        boolean flag = false;
        String localFileName = localPath + filename;
        fileFormat = fileFormat == null ? "" : fileFormat
                .trim();
        fileEndFormat = fileEndFormat == null ? ""
                : fileEndFormat.trim();
        // 三种情况
        if (fileFormat.length() > 0 && fileEndFormat.length() > 0) {
            if (filename.startsWith(fileFormat) && filename.endsWith(fileEndFormat)) {
                flag = downloadFile(remotePath, filename, localPath, filename);
                if (flag) {
                    filenames.add(localFileName);
                    if (flag && del) {
                        deleteSFTP(remotePath);
                    }
                }
            }
        } else if (fileFormat.length() > 0 && "".equals(fileEndFormat)) {
            if (filename.startsWith(fileFormat)) {
                flag = downloadFile(remotePath, filename, localPath, filename);
                if (flag) {
                    filenames.add(localFileName);
                    if (flag && del) {
                        deleteSFTP(remotePath);
                    }
                }
            }
        } else if (fileEndFormat.length() > 0 && "".equals(fileFormat)) {
            if (filename.endsWith(fileEndFormat)) {
                flag = downloadFile(remotePath, filename, localPath, filename);
                if (flag) {
                    filenames.add(localFileName);
                    if (flag && del) {
                        deleteSFTP(remotePath);
                    }
                }
            }
        } else {
            flag = downloadFile(remotePath, filename, localPath, filename);
            if (flag) {
                filenames.add(localFileName);
                if (flag && del) {
                    deleteSFTP(remotePath);
                }
            }
        }

        return filenames;
    }

    //2.打开或者进入指定目录
    public static boolean openDir(String directory, ChannelSftp sftp) {
        try {
            sftp.cd(directory);
            return true;
        } catch (SftpException e) {
            log.error(e + "");
            return false;
        }
    }

    //4.遍历一个目录，并得到这个路径的集合list（等递归把这个目录下遍历结束后list存放的就是这个目录下的所有文件的路径集合）
    public static boolean getSftpPathList(String pathName, ChannelSftp sftp) throws IOException, SftpException {
        if (!pathName.endsWith("/")) {
            pathName = pathName + "/";
        }
        boolean fl = false;
        boolean flag = openDir(pathName, sftp);
        if (flag) {
            Vector vv = sftp.ls(pathName);
            if (vv == null && vv.size() == 0) {
                return false;
            } else {
                for (Object object : vv) {
                    ChannelSftp.LsEntry entry = (ChannelSftp.LsEntry) object;
                    String filename = entry.getFilename();
                    if (".".equals(filename) || "..".equals(filename)) {
                        continue;
                    }
                    if (openDir(pathName + filename + "/", sftp)) {
                        //能打开，说明是目录，接着遍历
                        getSftpPathList(pathName + filename + "/", sftp);
                    } else {
                        sftpList.add(pathName + filename);
                    }
                }
            }
        } else {
            log.info("对应的目录" + pathName + "不存在！");
        }
        if (sftpList != null && sftpList.size() > 0) {
            fl = true;
        }
        return fl;
    }

    /**
     * 下载单个文件
     *
     * @param remotePath：远程下载目录(以路径符号结束)e
     * @param remoteFileName：下载文件名
     * @param localPath：本地保存目录(以路径符号结束)
     * @param localFileName：保存文件名
     * @return
     */
    public static boolean downloadFile(String remotePath, String remoteFileName, String localPath, String localFileName) {
        FileOutputStream fieloutput = null;
        try {
            // sftp.cd(remotePath);
            File file = new File(localPath + localFileName);
            // mkdirs(localPath + localFileName);
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            fieloutput = new FileOutputStream(file);
            sftp.get(remotePath + remoteFileName, fieloutput);
            if (log.isInfoEnabled()) {
                log.info("===DownloadFile:" + remoteFileName + " success from sftp.");
            }
            return true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (SftpException e) {
            e.printStackTrace();
        } finally {
            if (null != fieloutput) {
                try {
                    fieloutput.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    /**
     * 上传单个文件
     *
     * @param remotePath：远程保存目录
     * @param localPath：本地上传目录(以路径符号结束)
     * @return
     */
    public static void uploadFile(String remotePath, String localPath) throws Exception {
        FileInputStream in = null;
        File localFile = new File(localPath);
        sftp.cd(remotePath);
        in = new FileInputStream(localFile);
        sftp.put(in, localFile.getName());

        if (in != null) {
            in.close();
        }
    }

    /**
     * 批量上传文件
     *
     * @param remotePath：远程保存目录
     * @param localPath：本地上传目录(以路径符号结束)
     * @return
     */
    public static boolean bacthUploadFile(String remotePath, String localPath) throws Exception {
        File localFile = new File(localPath);
        boolean flag = true;
        //进入远程路径
        try {
            if (!isDirExist(remotePath)) {
                sftp.mkdir(remotePath);
                sftp.cd(remotePath);
            } else {
                sftp.cd(remotePath);
            }
            //本地文件上传
            File file = new File(localPath);
            //本地文件上传方法
            copyFile(file, sftp.pwd(), true);


        } catch (Exception e) {
            e.printStackTrace();
            flag = false;
            throw e;
        }

        return flag;
    }

    private static void copyFile(File file, String pwd, Boolean singleFlag) throws Exception {
        if (!singleFlag) {
            copyFile(file, pwd);
        } else {
            copyFileSingle(file, pwd);
        }
    }

    private static void copyFileSingle(File file, String pwd) throws Exception {
        if (file.isDirectory()) {
            File[] list = file.listFiles();
            String fileName = file.getName();
            sftp.cd(pwd);
            System.out.println("正在创建目录:" + sftp.pwd() + "/" + fileName);
            sftp.mkdir(fileName);
            System.out.println("目录创建成功:" + sftp.pwd() + "/" + fileName);
            //远程路径发生改变
            pwd = pwd + "/" + file.getName();
            sftp.cd(file.getName());

            for (int i = 0; i < list.length; i++) {
                copyFile(list[i], pwd);
            }
        } else {
            File parentFile = file.getParentFile();
            String parentFileName = parentFile.getName();
            String middleDir = parentFile.getAbsolutePath().replace(FileListenerBootStarp.motionDirPath, "");
            String middleDirLinux = middleDir.replaceAll("\\\\", "/");

            if ("".equals(middleDir) && "\\".equals(middleDir)) {
                sftp.cd(pwd);
            } else {
                pwd = pwd + "/" + middleDirLinux;
                try {
                    sftp.mkdir(pwd);
                } catch (SftpException e) {
                    log.info("远程文件夹已经存在");
                }
                //不是目录,直接进入改变后的远程路径,进行上传
                sftp.cd(pwd);
            }

            System.out.println("正在复制文件:" + file.getAbsolutePath());
            InputStream instream = null;
            OutputStream outstream = null;
            outstream = sftp.put(file.getName());
            instream = new FileInputStream(file);

            byte b[] = new byte[1024];
            int n;
            while ((n = instream.read(b)) != -1) {
                outstream.write(b, 0, n);
            }

            outstream.flush();
            outstream.close();
            instream.close();

        }

    }


    private static void copyFile(File file, String pwd) throws Exception {
        if (file.isDirectory()) {
            File[] list = file.listFiles();
            String fileName = file.getName();
            sftp.cd(pwd);
            System.out.println("正在创建目录:" + sftp.pwd() + "/" + fileName);
            sftp.mkdir(fileName);
            System.out.println("目录创建成功:" + sftp.pwd() + "/" + fileName);
            //远程路径发生改变
            pwd = pwd + "/" + file.getName();
            sftp.cd(file.getName());

            for (int i = 0; i < list.length; i++) {
                copyFile(list[i], pwd);
            }
        } else {
            //不是目录,直接进入改变后的远程路径,进行上传
            sftp.cd(pwd);

            System.out.println("正在复制文件:" + file.getAbsolutePath());
            InputStream instream = null;
            OutputStream outstream = null;
            outstream = sftp.put(file.getName());
            instream = new FileInputStream(file);

            byte b[] = new byte[1024];
            int n;
            while ((n = instream.read(b)) != -1) {
                outstream.write(b, 0, n);
            }

            outstream.flush();
            outstream.close();
            instream.close();

        }

    }

    /**
     * 删除本地文件
     *
     * @param filePath
     * @return
     */
    public boolean deleteFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            return false;
        }

        if (!file.isFile()) {
            return false;
        }
        boolean rs = file.delete();
        if (rs && log.isInfoEnabled()) {
            log.info("delete file success from local.");
        }
        return rs;
    }

    /**
     * 创建目录
     *
     * @param createpath
     * @return
     */
    public static void createDir(String createpath) {
        try {
            if (isDirExist(createpath)) {
                sftp.cd(createpath);
            }
            String pathArry[] = createpath.split("/");
            StringBuffer filePath = new StringBuffer("/");
            for (String path : pathArry) {
                if (path.equals("")) {
                    continue;
                }
                filePath.append(path + "/");
                if (isDirExist(filePath.toString())) {
                    sftp.cd(filePath.toString());
                } else {
                    // 建立目录
                    sftp.mkdir(filePath.toString());
                    // 进入并设置为当前目录
                    sftp.cd(filePath.toString());
                }

            }
        } catch (SftpException e) {
            e.printStackTrace();
        }
    }

    /**
     * 判断目录是否存在
     *
     * @param directory
     * @return
     */
    public static boolean isDirExist(String directory) {
        try {
            Vector<?> vector = sftp.ls(directory);
            if (null == vector) {
                return false;
            } else {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 删除stfp文件
     *
     * @param directory：要删除文件所在目录
     */
    public static void deleteSFTP(String directory) {
        try {
            if (isDirExist(directory)) {
                Vector<ChannelSftp.LsEntry> vector = sftp.ls(directory);
                if (vector.size() == 1) { // 文件，直接删除
                    sftp.rm(directory);
                } else if (vector.size() == 2) { // 空文件夹，直接删除
                    sftp.rmdir(directory);
                } else {
                    String fileName = "";
                    // 删除文件夹下所有文件
                    for (ChannelSftp.LsEntry en : vector) {
                        fileName = en.getFilename();
                        if (".".equals(fileName) || "..".equals(fileName)) {
                            continue;
                        } else {
                            deleteSFTP(directory + "/" + fileName);
                        }
                    }
                    // 删除文件夹
                    sftp.rmdir(directory);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 如果目录不存在就创建目录
     *
     * @param path
     */
    public void mkdirs(String path) {
        File f = new File(path);

        String fs = f.getParent();

        f = new File(fs);

        if (!f.exists()) {
            f.mkdirs();
        }
    }

    /**
     * 默认连接的主机
     *
     * @throws Exception
     */
    public static void connectDefaultMachine() throws Exception {
        connect("106.13.130.208", "root", "liangchen582461!", 22);
    }

    /**
     * 批量上传文件(默认主机，默认远程路径)
     *
     * @param localPath 本地存放路径
     */
    public void bacthUploadFileDefault(String localPath) {
        // Sftp下载路径
        String sftpPath = "/home/sftp/";
        try {
            // 上传
            bacthUploadFile(sftpPath, localPath);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            sftp.disconnect();
        }
    }

    /**
     * 批量下载文件(默认主机，默认远程路径)
     *
     * @param localPath 本地存放路径
     */
    public static void bacthDownloadFileDefault(String localPath) {
        // Sftp下载路径
        String sftpPath = "/home/sftp/";
        try {
            // 下载
            batchDownLoadFile(sftpPath, localPath, null, null, false);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            sftp.disconnect();
        }
    }

    /**
     * 上传单个文件(默认主机，默认远程路径)
     *
     * @param localPath 本地存放路径
     */
    public void uploadFileDefault(String localPath) {
        // Sftp下载路径
        String sftpPath = "/home/sftp/";
        try {
            // 上传
            uploadFile(sftpPath, localPath);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            sftp.disconnect();
        }
    }

    /**
     * 测试
     */
    public static void main(String[] args) {
        // 本地存放地址
        String localPath = "D:\\temp\\apollo\\dabaojian\\";
        // Sftp下载路径
        String sftpPath = "/home/sftp/";
        List<String> filePathList = new ArrayList<String>();
        try {
            connectDefaultMachine();
            //deleteSFTP(sftpPath);
            // 上传
            bacthUploadFile(sftpPath, localPath);
            //deleteSFTP("/home/ftp/ff");
//            batchDownLoadFile(sftpPath, localPath, null, null, false);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            sftp.disconnect();
        }
    }
}