//package com.lc.filesync;
//
//import org.slf4j.Logger;
//
//import org.slf4j.LoggerFactory;
//
//import com.jcraft.jsch.ChannelSftp;
//import com.jcraft.jsch.JSch;
//import com.jcraft.jsch.JSchException;
//import com.jcraft.jsch.Session;
//import com.jcraft.jsch.SftpException;
//
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Vector;
//
//public class SftpUtils{
//private static Logger logger = LoggerFactory.getLogger(SftpUtils.class);
//private static Session session = null;
//private static ChannelSftp channel = null;
//private static List<String> sftpList=new ArrayList<>();
////1.获取sftp连接
//public static ChannelSftp connect(String host,int port,String username,String password){
//ChannelSftp sftp = null;
//JSch jsch = new JSch();
//    try{
//        jsch.getSesssion(username,host,port);
//        session=jsch.getSession(username,host,port);
//        session.setPassword(password);
//        Properties sshConfig=new Properties();
//        sshConfig.put("StrictHostKeyCheching","no");
//        session.setConfig(sshConfig);
//        session.connect();
//        logger.info("session connected!");
//        channel=(ChannelSftp)session.openChannel("sftp");
//        channel.connect();
//        sftp=(ChannelSftp)channel;
//    }catch(JSchException e){
//        logger.error("获取SFTP连接异常",e);
//    }
//    return sftp;
//}
//
////2.打开或者进入指定目录
//public static boolean openDir(String directory,ChannelSftp sftp){
//    try{
//        sftp.cd(directory);
//        return true;
//    }catch(SftpException e){
//        logger.error(e+"");
//        return false;
//    }
//}
//
////3.关闭资源
//public static void close(){
//    if(channel != null){
//        channel.disconnect();
//    }
//    if(session != null){
//        session.disconnect();
//    }
//}
//
////4.遍历一个目录，并得到这个路径的集合list（等递归把这个目录下遍历结束后list存放的就是这个目录下的所有文件的路径集合）
//public boolean getSftpPathList(String pathName,ChannelSftp sftp)throws IOException,SftpException{
//    boolean fl =false;
//    boolean flag=openDir(pathName,sftp);
//    if(flag){
//        Vector vv = sftp.ls(pathName);
//        if(vv == null && vv.size == 0){
//            return null;
//        }else{
//            for(Object object : vv){
//                ChannelSftp.LsEntry entry=(ChannelSftp.LsEntry)object;
//                String filename=entry.getFilename();
//                if(".".equeals(filename) || "..".equeals(filename)){
//                    continue;
//                }
//                if(openDir(pathName+filename+"/"),sftp){
//                    //能打开，说明是目录，接着遍历
//                    getSftpList(pathName+filename+"/"),sftp);
//                }else{
//                    sftpList.add(pathName+filename);
//                }
//            }
//        }
//    }else{
//        log.info("对应的目录"+directory+"不存在！");
//    }
//    if(sftpList != null && sftpList.size() > 0){
//        fl=true;
//    }
//    return fl;
// }
//
//}