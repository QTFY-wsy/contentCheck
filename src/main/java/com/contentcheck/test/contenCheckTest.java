package com.contentcheck.test;

import com.aliyuncs.green.extension.uploader.ClientUploader;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.contentcheck.ContenCheckApplication;
import com.contentcheck.image.action.ImageCheck;
import com.contentcheck.word.action.WordCheck;
import com.contentcheck.word.bean.AuditInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ContenCheckApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class contenCheckTest {
        @Autowired
        private WordCheck wordCheck;
        @Autowired
        private ImageCheck imageCheck;

        @Value("${aliyun.oss.regionId}")
        private String regionId;

        @Value("${aliyun.oss.accessKeyId}")
        private String accessKeyId;

        @Value("${aliyun.oss.accessKeySecret}")
        private String accessKeySecret;

        @Test
        public void testWordCheck() {
            List<String> content = new ArrayList<>();
            content.add("我是国家主席，嘿嘿！");
            content.add("1231312312");
            List<AuditInfo> auditInfos = wordCheck.textReviews(content);
            for(AuditInfo auditInfo : auditInfos){
                System.out.println(auditInfo.isResult()+":"+auditInfo.getMsg());
            }
        }


    @Test
    public void testImageheck() {
        IClientProfile profile = DefaultProfile.getProfile(regionId, accessKeyId, accessKeySecret);
        /**
         * 如果您要检测的文件存于本地服务器上，可以通过下述代码片生成URL。
         * 再将返回的URL作为图片地址传递到服务端进行检测。
         */
        String url = null;
        ClientUploader clientUploader = ClientUploader.getImageClientUploader(profile, false);
        try{
            url = clientUploader.uploadFile("C:\\Users\\wsy\\Desktop\\image/test1.jpg");
        }catch (Exception e){
            e.printStackTrace();
        }
        List<String> urlList = new ArrayList<>();
        urlList.add(url);
        List<AuditInfo> auditInfos = imageCheck.imageReviews(urlList);
        for(AuditInfo auditInfo : auditInfos){
            System.out.println(auditInfo.isResult()+":"+auditInfo.getMsg());
        }

    }
}
