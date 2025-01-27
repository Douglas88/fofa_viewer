package org.fofaviewer.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import javafx.scene.control.Alert;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import sun.misc.BASE64Encoder;
import javax.net.ssl.*;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.hash.Hashing;

public class RequestHelper {
    private static RequestHelper request = null;
    private Logger logger = null;
    private static final CloseableHttpClient httpClient = HttpClientBuilder.create().build();
    private final String[] ua = new String[]{
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 11_1_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.141 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:65.0) Gecko/20100101 Firefox/65.0",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.41 Safari/537.36 Edg/88.0.705.22"
    };

    private RequestHelper() {
        this.logger = Logger.getLogger("RequestHelper");
        LogUtil.setLogingProperties(logger);
    }

    public static RequestHelper getInstance() {
        if (request == null) {
            request = new RequestHelper();
        }
        return request;
    }

    private CloseableHttpResponse getResponse(String url) {
        CloseableHttpClient httpClient = HttpClientBuilder.create().build();
        CloseableHttpResponse response = null;
        HashMap<String, String> map = new HashMap<String, String>();
        try {
            URIBuilder builder = new URIBuilder(url);
            HttpGet httpGet = new HttpGet(builder.build());
            httpGet.setHeader("User-Agent", ua[(new SecureRandom()).nextInt(3)]);
            response = httpClient.execute(httpGet);
            return response;
        } catch (java.net.ConnectException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setHeaderText(null);
            alert.setContentText("网站访问异常！");
            alert.showAndWait();
            return null;
        } catch (Exception ex) {
            logger.log(Level.WARNING, ex.getMessage(), ex);
            return null;
        }
    }

    /**
     * 发起HTTP请求获取响应内容
     *
     * @param url 请求url
     * @return 响应内容
     * 200 : 请求响应内容
     * other code : request error
     * error ：请求失败
     */
    public HashMap<String, String> getHTML(String url) {
        CloseableHttpResponse response = getResponse(url);
        HashMap<String, String> result = new HashMap<>();
        if (response != null) {
            int code = response.getStatusLine().getStatusCode();
            result.put("code", String.valueOf(code));
            try {
                if (code == 200) {
                    HttpEntity httpEntity = response.getEntity();
                    result.put("msg", EntityUtils.toString(httpEntity, "utf8"));
                } else if (code == 401) {
                    result.put("msg", "请求错误状态码401，可能是没有在config中配置有效的email和key，或者您的账号权限不足无法使用api进行查询。");
                } else if (code == 502) {
                    result.put("msg", "请求错误状态码502，可能是账号限制了每次请求的最大数量，建议尝试修改config中的maxSize为100");
                } else {
                    result.put("msg", "请求响应错误,状态码" + String.valueOf(code));
                }
                return result;
            } catch (Exception e) {
                result.put("code", "error");
                result.put("msg", e.getMessage());
                logger.log(Level.WARNING, e.getMessage(), e);
                return result;
            } finally {
                try {
                    response.close();
                } catch (IOException ex) {
                    logger.log(Level.WARNING, ex.getMessage(), ex);
                }
            }
        }
        result.put("code", "error");
        return result;
    }

    /**
     * 提取网站favicon 需要两步：
     * 1. 直接访问url根目录的favicon，若404则跳转至第2步
     * 2. 访问网站，获取html页面，获取head中的 link标签的ico 路径
     *
     * @param url
     * @return
     */
    public HashMap<String, String> getImageFavicon(String url) {
        CloseableHttpResponse response = getResponse(url);
        HashMap<String, String> result = new HashMap<>();
        if (response != null) {
            int code = response.getStatusLine().getStatusCode();
            result.put("code", String.valueOf(code));
            if (code == 200) {
                try {
                    if (response.getEntity().getContentLength() == 0) {
                        logger.log(Level.FINE, url + "无响应内容");
                        return null;
                    }
                    InputStream is = response.getEntity().getContent();
                    byte[] buffer = new byte[1024];
                    ByteArrayOutputStream bos=new ByteArrayOutputStream();
                    int len = 0;
                    while((len = is.read(buffer))!=-1){
                        bos.write(buffer,0, len);
                    }
                    bos.flush();
                    String encoded = new BASE64Encoder().encode(Objects.requireNonNull(bos.toByteArray()));
                    String hash = getIconHash(encoded);
                    result.put("msg", "icon_hash=\"" + hash + "\"");
                    return result;
                } catch (Exception e) {
                    result.put("code", "error");
                    result.put("msg", e.getMessage());
                    logger.log(Level.WARNING, e.getMessage(), e);
                    return result;
                } finally {
                    try {
                        response.close();
                    } catch (IOException ex) {
                        logger.log(Level.WARNING, ex.getMessage(), ex);
                    }
                }
            }
        }
        return null;
    }

    public String getLinkIcon(String url) {
        HashMap<String, String> result = getHTML(url);
        if (result.get("code").equals("200")) {
            Document document = Jsoup.parse(result.get("msg"));
            Elements elements = document.getElementsByTag("link");
            if (elements.size() == 0) { // 没有link标签
                return null;
            } else {
                for (Element i : elements) {
                    String rel = i.attr("rel");
                    if (rel.equals("icon") || rel.equals("shortcut icon")) {
                        String href = i.attr("href");
                        if (href.startsWith("http")) { // link 显示完整url
                            return href;
                        } else if (href.startsWith("/")) {  // link 显示相对路径
                            return url + href;
                        }
                        return null;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 计算favicon hash
     *
     * @param f favicon的文件对象
     * @return favicon hash值
     */
    private String getIconHash(String f) {
        int murmu = Hashing.murmur3_32().hashString(f.replaceAll("\r", "") + "\n", StandardCharsets.UTF_8).asInt();
        return String.valueOf(murmu);
    }

    /**
     * 获取证书编号
     * @param host 域名
     * @return 证书编号
     */
    public String getCertSerialNum(String host) {
        try {
            URL url = new URL(host);
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            TrustModifier.relaxHostChecking(conn);
            conn.connect();
            Certificate[] certs = conn.getServerCertificates();
            X509Certificate cert = (X509Certificate) certs[0];
            return "cert=\"" + cert.getSerialNumber().toString() + "\"";
        } catch (Exception e) {
            logger.log(Level.FINER, e.getMessage(), e);
        }
        return null;
    }

    public List<String> getTips(String key) {
        try {
            key = java.net.URLEncoder.encode(key, "UTF-8");
            CloseableHttpResponse response = this.getResponse("https://api.fofa.so/v1/search/tip?q=" + key);
            if(response != null){
                if (response.getStatusLine().getStatusCode() == 200) {
                    HttpEntity httpEntity = response.getEntity();
                    String content = EntityUtils.toString(response.getEntity(), "utf8");
                    JSONObject obj = JSON.parseObject(content);
                    if(obj.getString("message").equals("ok")){
                        List<String> data = new ArrayList<>();
                        JSONArray objs = obj.getJSONArray("data");
                        for (Object o : objs) {
                            JSONObject tmp = (JSONObject) o;
                            data.add(tmp.getString("name") + "--" + tmp.getString("company"));
                        }
                        return data;
                    }
                }
            }
            return null;
        }catch (Exception e){
            logger.log(Level.WARNING, e.getMessage(), e);
            return null;
        }

    }

    /**
     * base64编码字符串
     *
     * @param str 字符串
     * @return 编码字符串
     */
    public static String encode(String str) {
        BASE64Encoder encoder = new BASE64Encoder();
        return encoder.encode(str.getBytes(StandardCharsets.UTF_8)).replaceAll("\n", "");
    }
}
