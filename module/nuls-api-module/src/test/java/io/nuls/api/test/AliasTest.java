package io.nuls.api.test;

import io.nuls.base.basic.AddressTool;
import io.nuls.base.data.Address;
import io.nuls.core.constant.BaseConstant;
import io.nuls.core.parse.JSONUtils;
import org.bouncycastle.util.encoders.Hex;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Niels
 */
public class AliasTest {

    public static void main(String[] args) throws IOException {
        String url = "http://192.168.1.37:8001/api/account/allalias";
        String result = sendGet(url, null);
        Map<String, Object> map = JSONUtils.json2map(result);
        Map<String, Object> alias = (Map<String, Object>) map.get("data");
        List<AliasDto> list = new ArrayList();
        for(String key:alias.keySet()){
            byte[] address1 = Hex.decode(key);
            String val = (String) alias.get(key);
            byte[] hash160 = new byte[20];
            System.arraycopy(address1,3,hash160,0,20);
            byte[] addressV2 = new Address(2, "tNULS", BaseConstant.DEFAULT_ADDRESS_TYPE, hash160).getAddressBytes();
            String a = AddressTool.getStringAddressByBytes(addressV2);
            AliasDto dto = new AliasDto(a,val);
            list.add(dto);
        }
        String array = JSONUtils.obj2json(list);
        System.out.println(array);
    }

    /**
     * 向指定URL发送GET方式的请求
     *
     * @param url   发送请求的URL
     * @param param 请求参数
     * @return URL 代表远程资源的响应
     */
    public static String sendGet(String url, String param) {
        String result = "";
        String urlName = url + "?" + param;
        try {
            URL realUrl = new URL(urlName);
            //打开和URL之间的连接
            URLConnection conn = realUrl.openConnection();
            //设置通用的请求属性
            conn.setRequestProperty("accept", "*/*");
            conn.setRequestProperty("connection", "Keep-Alive");
            conn.setRequestProperty("user-agent",
                    "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1;SV1)");
            //建立实际的连接
            conn.connect();
            //获取所有的响应头字段
            Map<String, List<String>> map = conn.getHeaderFields();
            //遍历所有的响应头字段
            for (String key : map.keySet()) {
                System.out.println(key + "-->" + map.get(key));
            }
            // 定义 BufferedReader输入流来读取URL的响应
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                result += line;
            }
        } catch (Exception e) {
            System.out.println("发送GET请求出现异常" + e);
            e.printStackTrace();
        }
        return result;
    }

    static class AliasDto {
        private final String address;
        private final String alias;

        public AliasDto (String address, String alias){
            this.address = address;
            this.alias = alias;
        }

        public String getAddress() {
            return address;
        }

        public String getAlias() {
            return alias;
        }
    }
}
