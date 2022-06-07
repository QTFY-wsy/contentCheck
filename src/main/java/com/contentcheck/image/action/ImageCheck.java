package com.contentcheck.image.action;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.oss.ClientException;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ServerException;
import com.aliyuncs.green.extension.uploader.ClientUploader;
import com.aliyuncs.green.model.v20180509.ImageSyncScanRequest;
import com.aliyuncs.green.model.v20180509.TextScanRequest;
import com.aliyuncs.http.FormatType;
import com.aliyuncs.http.HttpResponse;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.contentcheck.word.bean.AuditInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;

import java.io.UnsupportedEncodingException;
import java.util.*;

@Controller
public class ImageCheck {
    private  final  static Logger logger = LoggerFactory.getLogger(ImageCheck.class);

    @Value("${aliyun.oss.regionId}")
    private String regionId;

    @Value("${aliyun.oss.accessKeyId}")
    private String accessKeyId;

    @Value("${aliyun.oss.accessKeySecret}")
    private String accessKeySecret;

    //设置获取client
    private IAcsClient getClient() {
        IClientProfile profile = DefaultProfile.getProfile(regionId, accessKeyId, accessKeySecret);
        //下面走的是阿帕奇的,自行选择
        // DefaultProfile profile1 = DefaultProfile.getProfile(region, accessKeyId, accessKeySecret);
        return new DefaultAcsClient(profile);
    }

    //设置图片审核请求头
    private ImageSyncScanRequest getImageSyncScanRequest() {
        ImageSyncScanRequest imageSyncScanRequest = new ImageSyncScanRequest();
        imageSyncScanRequest.setAcceptFormat(FormatType.JSON); // 指定api返回格式
        imageSyncScanRequest.setMethod(com.aliyuncs.http.MethodType.POST); // 指定请求方法
        imageSyncScanRequest.setEncoding("utf-8");
        imageSyncScanRequest.setRegionId(regionId);
        imageSyncScanRequest.setConnectTimeout(3000);
        imageSyncScanRequest.setReadTimeout(6000);
        return imageSyncScanRequest;
    }

    //请求参数封装 JSONObject (linkhashMap)
    private JSONObject getExecuteJSONObject(List<String> tasks) {
        JSONObject resultMap = new JSONObject();
        JSONArray inputBodyList = new JSONArray();
        for (String task : tasks) {
            JSONObject requestBody = new JSONObject();
            requestBody.put("dataId", UUID.randomUUID().toString());
            requestBody.put("url", task);
            requestBody.put("time", new Date());
            inputBodyList.add(requestBody);
        }
        /**
         * porn: 色情
         * terrorism: 暴恐
         * qrcode: 二维码
         * ad: 图片广告
         * ocr: 文字识别
         */
        resultMap.put("scenes", Arrays.asList("porn", "terrorism"));
        resultMap.put("tasks", inputBodyList);
        return resultMap;
    }

    //批量效验,阿里的api 详情批量限制100个,单个长度不能超过10000

    public List<AuditInfo> imageReviews(List<String> content)  {
        List<AuditInfo> result = new LinkedList<>();
        IAcsClient client = getClient();
        ImageSyncScanRequest imageSyncScanRequest = getImageSyncScanRequest();
        JSONObject imageData = getExecuteJSONObject(content);
        try {
            imageSyncScanRequest.setHttpContent(imageData.toJSONString().getBytes("UTF-8"), "UTF-8", FormatType.JSON);
            logger.info("阿里图片检测:[start]:\n {}", imageData.toJSONString().getBytes("UTF-8"));
            HttpResponse httpResponse = client.doAction(imageSyncScanRequest);
            logger.info("阿里图片检测:[end]:\n {}", httpResponse.getHttpContentString());
            if (httpResponse.isSuccess()) {
                logger.info("图片效验 成功");
                JSONObject scrResponse = JSON.parseObject(org.apache.commons.codec.binary.StringUtils.newStringUtf8(httpResponse.getHttpContent()));
                System.out.println(JSON.toJSONString(scrResponse, true));
                if (200 == scrResponse.getInteger("code")) {
                    JSONArray taskResults = scrResponse.getJSONArray("data");
                    for (int i = 0; i < taskResults.size(); i++) {
                        JSONObject taskResultObj = taskResults.getJSONObject(i);
                        AuditInfo auditInfo = new AuditInfo();
                        if (200 == taskResultObj.getInteger("code")) {


                            
                            JSONArray sceneResults = taskResultObj.getJSONArray("results");
                            for (int j = 0; j < sceneResults.size(); j++) {
                                JSONObject taskSubObject = sceneResults.getJSONObject(j);
                                String scene = taskSubObject.getString("scene");
                                String suggestion = taskSubObject.getString("suggestion");
                                String label = taskSubObject.getString("label");
                                double rate = taskSubObject.getDouble("rate");
                                auditInfo = convertMsg(label, auditInfo, rate);
                                result.add(auditInfo);
                            }
                        } else {
                            System.out.println("task process fail:" + taskResultObj.getInteger("code"));
                            logger.error("阿里图片检测:请求超时！");
                        }
                    }
                } else {
                    logger.error("检测状态失败 code:{}", scrResponse.getInteger("code"));
                }
            }
        } catch (ClientException e) {
            logger.error("请求调用失败,检查是否是超时");
            e.printStackTrace();
        } catch (ServerException e) {
            e.printStackTrace();
        } catch (com.aliyuncs.exceptions.ClientException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return result;
    }

    //标签效验
    private AuditInfo convertMsg(String label, AuditInfo audit, double rate) {
        //正常放行  normal：正常文本 spam：含垃圾信息 ad：广告 flood：灌水  meaningless：无意义 customized：自定义（例如命中自定义关键词）
        //拦截 politics：涉政 terrorism：暴恐 abuse：辱骂 porn：色情 contraband：违禁
        audit.setResult(true);
        audit.setMsg("审核正常");
        switch (label) {
            case "normal":
                break;
            case "spam":
                if (rate > 50.0) {
                    audit.setResult(true);
                    audit.setMsg("含垃圾信息");
                }
                break;
            case "ad":
                if (rate > 50.0) {
                    audit.setResult(true);
                    audit.setMsg("广告");
                }
                break;
            case "politics":
                audit.setResult(false);
                audit.setMsg("涉政");
                break;
            case "terrorism":
                audit.setResult(false);
                audit.setMsg("暴恐");
                break;
            case "abuse":
                if (rate > 70.0) {
                    audit.setResult(false);
                    audit.setMsg("辱骂");
                }
                break;
            case "porn":
                if (rate > 90.0) {
                    audit.setResult(false);
                    audit.setMsg("色情");
                }
                break;
            case "flood":
                if (rate > 95.0) {
                    audit.setResult(true);
                    audit.setMsg("灌水");
                }
                break;
            case "contraband":
                audit.setResult(false);
                audit.setMsg("违禁");
                break;
            case "meaningless":
                if (rate > 95.0) {
                    audit.setResult(true);
                    audit.setMsg("无意义");
                }
                break;
            case "qrcode":
                if (rate > 60.0) {
                    audit.setResult(true);
                    audit.setMsg("二维码");
                }
                break;
            default:
                audit.setResult(true);
                audit.setMsg("自定义");
                break;
        }
        return audit;
    }
    
}
