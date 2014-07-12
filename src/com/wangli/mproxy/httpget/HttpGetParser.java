package com.wangli.mproxy.httpget;

import com.wangli.mproxy.bean.ProxyRequest;
import com.wangli.mproxy.bean.ProxyResponse;
import com.wangli.mproxy.utils.CommonUtil;
import com.wangli.mproxy.utils.Config;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Http报文处理类
 *
 */
public class HttpGetParser {
    final static public String TAG = "HttpParser";
    final static private String RANGE_PARAMS="Range: bytes=";
    final static private String RANGE_PARAMS_0="Range: bytes=0-";
    final static private String CONTENT_RANGE_PARAMS="Content-Range: bytes ";
    
    private static final  int HEADER_BUFFER_LENGTH_MAX = 1024 * 10;
    private byte[] headerBuffer = new byte[HEADER_BUFFER_LENGTH_MAX];
    private int headerBufferLength=0;
    
    /** 链接带的端口 */
    private int remotePort=-1;
    /** 远程服务器地址 */
    private String remoteHost;
    /** 代理服务器使用的端口 */
    private int localPort;
    /** 本地服务器地址 */
    private String localHost;
    
    public HttpGetParser(String rHost,int rPort,String lHost,int lPort){
        remoteHost=rHost;
        remotePort =rPort;
        localHost=lHost;
        localPort=lPort;
    }
    
    public void clearHttpHeader(){
        headerBuffer = new byte[HEADER_BUFFER_LENGTH_MAX];
        headerBufferLength=0;
    }
    
    /**
     * 获取Request报文
     * @param source
     * @param length
     * @return
     */
    public byte[] getRequestMessage(byte[] source,int length){
        List<byte[]> httpRequest=getHttpMessage(Config.HTTP_REQUEST_BEGIN, 
                Config.HTTP_BODY_END, 
                source,
                length);
        if(httpRequest.size()>0){
            return httpRequest.get(0);
        }
        return null;
    }
    
    /**
     * Request报文解析转换ProxyRequest
     * @param bodyBytes
     * @return
     */
    public ProxyRequest getProxyRequest(byte[] bodyBytes){
        ProxyRequest result=new ProxyRequest();
        //获取Body
        result.message=new String(bodyBytes);
        
        // 把request中的本地ip改为远程ip
        result.message = result.message.replace(localHost, remoteHost);
        // 把代理服务器端口改为原URL端口
        if (remotePort ==-1)
            result.message = result.message.replace(":" + localPort, "");
        else
            result.message = result.message.replace(":" + localPort, ":"+ remotePort);
        //不带Range则添加补上，方便后面处理
        if(result.message.contains(RANGE_PARAMS)==false)
            result.message = result.message.replace(Config.HTTP_BODY_END,
                    "\r\n"+RANGE_PARAMS_0+Config.HTTP_BODY_END);
        Log.i(TAG, result.message);

        //获取Range的位置
        String rangePosition=CommonUtil.getSubString(result.message,RANGE_PARAMS,"-");
        Log.i(TAG,"------->rangePosition:"+rangePosition);
        result.rangePosition = Integer.valueOf(rangePosition);
        
        return result;
    }
    
    /**
     * 获取ProxyResponse
     * @param source
     * @param length
     */
    public ProxyResponse getProxyResponse(byte[] source,int length){
        List<byte[]> httpResponse=getHttpMessage(Config.HTTP_RESPONSE_BEGIN, 
                Config.HTTP_BODY_END, 
                source,
                length);
        
        if (httpResponse.size() == 0)
            return null;
        
        ProxyResponse result=new ProxyResponse();
        
        //获取Response正文
        result.header=httpResponse.get(0);
        String text = new String(result.header);
        
        Log.i(TAG + "<---", text);
        //获取二进制数据
        if(httpResponse.size()==2)
            result.body = httpResponse.get(1);
        
        //样例：Content-Range: bytes 2267097-257405191/257405192
        try {
            // 获取起始位置
            String currentPosition = CommonUtil.getSubString(text,CONTENT_RANGE_PARAMS, "-");
            result.currentPosition = Integer.valueOf(currentPosition);

            // 获取最终位置
            String startStr = CONTENT_RANGE_PARAMS + currentPosition + "-";
            String duration = CommonUtil.getSubString(text, startStr, "/");
            result.duration = Integer.valueOf(duration);
        } catch (Exception ex) {
            Log.e(TAG, CommonUtil.getExceptionMessage(ex));
        }
        return result;
    }
    
    /**
     * 替换Request报文中的Range位置,"Range: bytes=0-" -> "Range: bytes=xxx-"
     * @param requestStr
     * @param position
     * @return
     */
    public String modifyRequestRange(String requestStr,int position){
        String str=CommonUtil.getSubString(requestStr, RANGE_PARAMS, "-");
        str=str+"-";
        String result = requestStr.replaceAll(str, position+"-");
        return result;
    }
    
    /**
     * 获取HTTP报文
     * @param beginStr  开始的字符串
     * @param endStr    结束的字符串
     * @param source    数据源
     * @param length    长度
     * @return
     */
    private List<byte[]> getHttpMessage(String beginStr,String endStr,byte[] source,int length){
        if((headerBufferLength+length)>=headerBuffer.length){//如果缓存超过了headerbuffer的长度，就清空。
            clearHttpHeader();
        }
        
        System.arraycopy(source, 0, headerBuffer, headerBufferLength, length);
        headerBufferLength+=length;
        
        List<byte[]> result = new ArrayList<byte[]>();
        String headerStr = new String(headerBuffer);
        if (headerStr.contains(beginStr)
                && headerStr.contains(endStr)) {//
            
            int startIndex=headerStr.indexOf(beginStr, 0);
            int endIndex = headerStr.indexOf(endStr, startIndex);
            endIndex+=endStr.length();
            
            byte[] header=new byte[endIndex-startIndex];
            System.arraycopy(headerBuffer, startIndex, header, 0, header.length);
            result.add(header);
            
            if (headerBufferLength > header.length) {//还有数据
                byte[] body = new byte[headerBufferLength - header.length];
                System.arraycopy(headerBuffer, header.length, body, 0,body.length);
                result.add(body);
            }
            clearHttpHeader();
        }
        
        return result;
    }
    
}
