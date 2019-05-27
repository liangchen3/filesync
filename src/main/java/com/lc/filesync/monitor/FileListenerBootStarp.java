package com.lc.filesync.monitor;

import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.HiddenFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.springframework.util.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * @ClassName FileListemerBootStarp
 * @Description TODO
 * @Author 梁臣
 * @Date 2019/5/22 13:55
 * @Version 1.0
 **/
public class FileListenerBootStarp {
    public static String motionDirPath;

    public static void main(String[] args) throws Exception {
        //java对话框 参考文章:https://www.cnblogs.com/jiangxiulian/p/5961990.html
        String rootDir = null;
        while (true) {
            String inputValue = JOptionPane.showInputDialog("请输入一个监听目录!!!");
            if (!StringUtils.isEmpty(inputValue)) {
                rootDir = inputValue;
                File file = new File(rootDir);
                if (!file.exists()) {
                    int res = JOptionPane.showConfirmDialog(null, "输入的目录不存在! 是否继续输入", "是否继续输入", JOptionPane.YES_NO_OPTION);
                    if (res == JOptionPane.YES_OPTION) {
                        System.out.println("选择是后执行的代码");    //点击“是”后执行这个代码块
                        continue;
                    } else {
                        System.out.println("选择否后执行的代码");    //点击“否”后执行这个代码块
                        return;
                    }
                }
                break;
            }
        }
        start(rootDir);
    }

    public static void start(String rootDir) {
        try {
            System.out.println("开始监听...........");
            // 监控目录
            System.out.println("监听目录: -------> " + rootDir);
            motionDirPath=rootDir;
            // 轮询间隔 5 秒
            long interval = TimeUnit.SECONDS.toMillis(1);
            // 创建过滤器
            IOFileFilter directories = FileFilterUtils.and(
                    FileFilterUtils.directoryFileFilter(),
                    HiddenFileFilter.VISIBLE);
            IOFileFilter files = FileFilterUtils.and(
                    FileFilterUtils.fileFileFilter(),
                    FileFilterUtils.suffixFileFilter(".txt"));
            IOFileFilter filter = FileFilterUtils.or(directories, files);
            // 使用过滤器
            FileAlterationObserver observer = new FileAlterationObserver(new File(rootDir), filter);
            //不使用过滤器
            //FileAlterationObserver observer = new FileAlterationObserver(new File(rootDir));
            observer.addListener(new FileListener());
            //创建文件变化监听器
            FileAlterationMonitor monitor = new FileAlterationMonitor(interval, observer);
            // 开始监控
            monitor.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
