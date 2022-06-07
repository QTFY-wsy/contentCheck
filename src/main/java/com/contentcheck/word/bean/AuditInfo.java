package com.contentcheck.word.bean;

public class AuditInfo {

    /**
     * 审核结果
     */
    private boolean result;

    /**
     * 返回的消息
     */
    private String msg;

    /**
     * 内容
     */
    private String content;

    public boolean isResult() {
        return result;
    }

    public void setResult(boolean result) {
        this.result = result;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
