package com.contentcheck.word.action;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.oss.ClientException;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ServerException;
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
public class WordCheck {
    private  final  static Logger logger = LoggerFactory.getLogger(WordCheck.class);

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

    //设置文本审核请求头
    private TextScanRequest  getTextScanRequest() {
        TextScanRequest textScanRequest = new TextScanRequest();
        textScanRequest.setAcceptFormat(FormatType.JSON); // 指定API返回格式。
        textScanRequest.setHttpContentType(FormatType.JSON);
        textScanRequest.setMethod(com.aliyuncs.http.MethodType.POST); // 指定请求方法。
        textScanRequest.setEncoding("UTF-8");
        textScanRequest.setRegionId(regionId);
        textScanRequest.setConnectTimeout(3000);
        textScanRequest.setReadTimeout(6000);
        return textScanRequest;
    }

    //请求参数封装 JSONObject (linkhashMap)
    private JSONObject getExecuteJSONObject(List<String> tasks) {
        JSONObject resultMap = new JSONObject();
        JSONArray inputBodyList = new JSONArray();
        for (String task : tasks) {
            JSONObject requestBody = new JSONObject();
            requestBody.put("dataId", UUID.randomUUID().toString());
            requestBody.put("content", task); // 待检测的文本，长度不超过10000个字符*/
            inputBodyList.add(requestBody);
        }
        resultMap.put("scenes", Collections.singletonList("antispam")); // 检测场景，文本垃圾检测传递：antispam
        resultMap.put("tasks", inputBodyList);
        return resultMap;
    }

    //批量效验,阿里的api 详情批量限制100个,单个长度不能超过10000

    public List<AuditInfo> textReviews(List<String> content)  {
        List<AuditInfo> result = new LinkedList<>();
        IAcsClient client = getClient();
        TextScanRequest textScanRequest = getTextScanRequest();
        JSONObject data = getExecuteJSONObject(content);
        try {
            textScanRequest.setHttpContent(data.toJSONString().getBytes("UTF-8"), "UTF-8", FormatType.JSON);
            logger.info("阿里敏感词检测:[start]:\n {}", data.toJSONString().getBytes("UTF-8"));
            HttpResponse httpResponse = client.doAction(textScanRequest);
            logger.info("阿里敏感词检测:[end]:\n {}", httpResponse.getHttpContentString());
            if (httpResponse.isSuccess()) {
                logger.info("敏感词效验 成功");
                JSONObject scrResponse = JSON.parseObject(new String(httpResponse.getHttpContent(), "UTF-8"));
                if (200 == scrResponse.getInteger("code")) {
                    JSONArray taskResults = scrResponse.getJSONArray("data");
                    for (int i = 0; i < taskResults.size(); i++) {
                        JSONObject taskResultObj = taskResults.getJSONObject(i);
                        AuditInfo auditInfo = new AuditInfo();
                        //如果被检测文本命中了自定义关键词词库中的关键词，则会返回当前字段，并将命中的关键词替换为星号（*）。
                        String filteredContent = taskResultObj.getString("filteredContent");
                        auditInfo.setContent(filteredContent);
                        if (200 == taskResultObj.getInteger("code")) {
                            JSONArray sceneResults = taskResultObj.getJSONArray("results");
                            for (int j = 0; j < sceneResults.size(); j++) {
                                JSONObject taskSubObject = sceneResults.getJSONObject(j);
                                //这里检测只使用一个 result 结果,检测文本为一个
                                String scene = taskSubObject.getString("scene");
                                String suggestion = taskSubObject.getString("suggestion");
                                String label = taskSubObject.getString("label");
                                double rate = taskSubObject.getDouble("rate");
                                auditInfo = convertMsg(label, auditInfo, rate);
                                result.add(auditInfo);
                            }
                        } else {
                            System.out.println("task process fail:" + taskResultObj.getInteger("code"));
                            logger.error("阿里敏感词检测:请求超时！");
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
