package io.nuls.api.test;

import io.nuls.base.basic.AddressTool;
import io.nuls.base.data.Address;
import io.nuls.core.constant.BaseConstant;
import io.nuls.core.parse.JSONUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Niels
 */
public class AddressTest {
    /**
     * 用于从1.0获取余额快照，并将地址转换为2.0格式。
     *
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        String url = "http://116.62.135.185:8081";
        String example = "{: \"2.0\", method: \"getCoinRanking\", params: [271, 15, 0], id: 5898}";
        List<Account> resultList = new ArrayList<>();

        int index = 1;
        Map<String, Object> map = new HashMap<>();
        map.put("jsonrpc", "2.0");
        map.put("method", "getCoinRanking");
        List<Integer> list = new ArrayList<>();

        while (true) {
            list.clear();
            list.add(index);
            list.add(100);
            list.add(0);
            map.put("params", list);
            map.put("id", index++);
            String params = JSONUtils.obj2json(map);
            String response = sendPost(url, params);
            Map<String, Object> result = JSONUtils.json2map(response);
            Map<String, Object> data = (Map<String, Object>) result.get("result");
            List<Map<String, Object>> addressList = (List<Map<String, Object>>) data.get("list");
            for (Map<String, Object> addressInfo : addressList) {
                int type = (int) addressInfo.get("type");
                if (type != 1) {
                    continue;
                }
                String address = (String) addressInfo.get("address");
                long totalBalance = Long.parseLong("" + addressInfo.get("totalBalance"));
                resultList.add(new Account(address, totalBalance));
            }
            if (addressList.isEmpty()) {
                break;
            }
        }
        long total = 0L;
        List<GenesisItem> itemList = new ArrayList<>();
        for (Account account : resultList) {
            byte[] addressV1 = io.nuls.sdk.core.utils.AddressTool.getAddress(account.getAddressV1());
            byte[] bytesV1 = new byte[20];
            System.arraycopy(addressV1, 3, bytesV1, 0, 20);
            byte[] addressV2 = new Address(2, "tNULS", BaseConstant.DEFAULT_ADDRESS_TYPE, bytesV1).getAddressBytes();
            account.setAddressV2(AddressTool.getStringAddressByBytes(addressV2));
            total += account.getBalance();
            itemList.add(new GenesisItem(account.getAddressV2(), account.getBalance()));
        }
        System.out.println("total : " + total);
        System.out.println(JSONUtils.obj2json(itemList));
    }

    /**
     * 向指定URL发送POST方式的请求
     *
     * @param url   发送请求的URL
     * @param param 请求参数
     * @return URL 代表远程资源的响应
     */
    public static String sendPost(String url, String param) {
        String result = "";
        try {
            URL realUrl = new URL(url);
            //打开和URL之间的连接
            URLConnection conn = realUrl.openConnection();
            //设置通用的请求属性
            conn.setRequestProperty("accept", "*/*");
            conn.setRequestProperty("connection", "Keep-Alive");
            conn.setRequestProperty("user-agent",
                    "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1;SV1)");
            //发送POST请求必须设置如下两行
            conn.setDoOutput(true);
            conn.setDoInput(true);
            //获取URLConnection对象对应的输出流
            PrintWriter out = new PrintWriter(conn.getOutputStream());
            //发送请求参数
            out.print(param);
            //flush输出流的缓冲
            out.flush();
            // 定义 BufferedReader输入流来读取URL的响应
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
            String line;
            while ((line = in.readLine()) != null) {
                result += "\n" + line;
            }
        } catch (Exception e) {
            System.out.println("发送POST请求出现异常" + e);
            e.printStackTrace();
        }
        return result;
    }

    static class Account {
        private String addressV1;
        private long balance;
        private String addressV2;

        public Account(String addressV1, long balance) {
            this.addressV1 = addressV1;
            this.balance = balance;
        }

        public String getAddressV1() {
            return addressV1;
        }

        public void setAddressV1(String addressV1) {
            this.addressV1 = addressV1;
        }

        public long getBalance() {
            return balance;
        }

        public void setBalance(long balance) {
            this.balance = balance;
        }

        public String getAddressV2() {
            return addressV2;
        }

        public void setAddressV2(String addressV2) {
            this.addressV2 = addressV2;
        }
    }

    static class GenesisItem {
        public GenesisItem(String address, long amount) {
            this.address = address;
            this.amount = amount;
        }

        private String address;
        private long amount;
        private long lockTime = 0;

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public long getAmount() {
            return amount;
        }

        public void setAmount(long amount) {
            this.amount = amount;
        }

        public long getLockTime() {
            return lockTime;
        }

        public void setLockTime(long lockTime) {
            this.lockTime = lockTime;
        }
    }
}
