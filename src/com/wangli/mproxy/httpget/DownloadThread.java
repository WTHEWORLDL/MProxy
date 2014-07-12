
package com.wangli.mproxy.httpget;

import com.wangli.mproxy.utils.CommonUtil;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 下载模块，支持断点下载
 */
public class DownloadThread extends Thread {
    static private final String TAG = "DownloadThread";
    private String mUrl;
    private long mDownloadSize;
    private String filePath;
    private String cacheDir;
    private int mBufferSize;//要下载的文件大小
    private int maxCacheFileNumber;
    private boolean mStop;
    private boolean mDownloading;
    private boolean mStarted;
    private boolean mError;

    /**
     * @param url   下载的url
     * @param dir   缓存目录
     * @param fileName  缓存文件名称
     * @param maxCacheFileNumber    最大的缓存文件数量
     * @param targetSize    预加载的数据大小
     */
    public DownloadThread(String url, String cacheDir, String fileName,int maxCacheFileNumber, int mBufferSize) {
        mUrl = url;

        this.cacheDir = cacheDir;
        filePath = cacheDir + File.separator + fileName;
        
        this.maxCacheFileNumber = maxCacheFileNumber;
        // 如果文件存在，则继续
        File file = new File(filePath);
        if (file.exists()) {
            mDownloadSize = file.length();
        } else {
            mDownloadSize = 0;
        }

        this.mBufferSize = mBufferSize;
        mStop = false;
        mDownloading = false;
        mStarted = false;
        mError = false;
    }

    @Override
    public void run() {
        mDownloading = true;
        download();
    }

    /** 启动下载线程 */
    public void startThread() {
        if (!mStarted) {
            this.start();

            // 只能启动一次
            mStarted = true;
        }
    }

    /** 停止下载线程 */
    public void stopThread() {
        mStop = true;
    }

    /** 是否正在下载 */
    public boolean isDownloading() {
        return mDownloading;
    }

    /**
     * 是否下载异常
     * 
     * @return
     */
    public boolean isError() {
        return mError;
    }

    public long getDownloadedSize() {
        return mDownloadSize;
    }

    /** 是否下载成功 */
    public boolean isDownloadSuccessed() {
        return (mDownloadSize != 0 && mDownloadSize >= mBufferSize);
    }

    private void download() {
        // 下载成功则关闭
        if (isDownloadSuccessed()) {
            Log.i(TAG, "...DownloadSuccessed...");
            return;
        }
        InputStream is = null;
        FileOutputStream os = null;
        if (mStop) {
            return;
        }
        try {
            URL url = new URL(mUrl);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.setInstanceFollowRedirects(true);// 允许重定向
            is = urlConnection.getInputStream();
            if (mDownloadSize == 0) {// 全新文件

              //清除多余的缓存文件，因为下面要开始写一个文件，所以现在要多删一个文件，这样才能保证写了新文件之后，目录中的数量保证是不超过最大数量。
              CommonUtil.deleteExcessFiles(cacheDir,maxCacheFileNumber-1);
                
                os = new FileOutputStream(filePath);
                Log.i(TAG, "download file:" + filePath);
            }
            else {// 追加数据
                os = new FileOutputStream(filePath, true);
                Log.i(TAG, "append exists file:" + filePath);
            }
            int len = 0;
            byte[] bs = new byte[1024];
            if (mStop) {
                return;
            }
            while (!mStop // 未强制停止
                    && mDownloadSize < mBufferSize // 未下载足够
                    && ((len = is.read(bs)) != -1)) {// 未全部读取
                os.write(bs, 0, len);
                mDownloadSize += len;
            }
        } catch (Exception e) {
            mError = true;
            Log.i(TAG, "download error:" + e.toString() + "");
            Log.i(TAG, CommonUtil.getExceptionMessage(e));
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                }
            }

            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
            mDownloading = false;
            // 清除空文件
            File nullFile = new File(filePath);
            if (nullFile.exists() && nullFile.length() == 0)
                nullFile.delete();

            Log.i(TAG, "mDownloadSize:" + mDownloadSize + ",mBufferSize:" + mBufferSize);
        }
    }
}
