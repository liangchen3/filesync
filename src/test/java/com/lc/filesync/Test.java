package com.lc.filesync;

/**
 * @ClassName Test
 * @Description TODO
 * @Author 梁臣
 * @Date 2019/5/22 19:29
 * @Version 1.0
 * @QQ 1160329468
 * @PhoneNum 15732142131
 **/
public class Test {

    public static void main(String[] args) {
        String remoteDir="/home/";
        String localPath="D:\\";
        String filePath = "/home/a/b/lc.txt";

        String remotePathSub = filePath.substring(filePath.lastIndexOf("/") + 1);
        String remotePath = filePath.substring(0, filePath.lastIndexOf("/") + 1);
        String middleDir=remotePath.substring(remoteDir.length()).replaceAll("/","\\\\");

//        System.out.println("1:" + remotePathSub[0]);
//        System.out.println("2:" + remotePathSub[1]);
        System.out.println("1:" + remotePath);
        System.out.println("2:" + remotePathSub);
        System.out.println("3:" + middleDir);

    }

}
