
package com.wangli.mproxy.bean;

public class ProxyResponse {
    public byte[] header;//报文的消息头
    public byte[] body;//报文的消息体
    public long currentPosition;
    public long duration;
}
