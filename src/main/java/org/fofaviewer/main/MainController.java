package org.fofaviewer.main;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.input.*;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.fofaviewer.bean.FofaBean;
import org.fofaviewer.bean.TableBean;
import org.fofaviewer.controls.AutoHintTextField;
import org.fofaviewer.utils.LogUtil;
import org.fofaviewer.utils.RequestHelper;
import org.fofaviewer.controls.CloseableTabPane;
import java.io.*;
import java.math.BigInteger;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 处理事件
 */
public class MainController {
    @FXML
    private VBox rootLayout;
    @FXML
    private TextField queryTF;
    @FXML
    private CheckBox checkHoneyPot;
    @FXML
    private CloseableTabPane tabPane;
    private final RequestHelper helper = RequestHelper.getInstance();
    private FofaBean client;
    private Logger logger = null;

    public MainController(){
        this.logger = Logger.getLogger("Controller");
        LogUtil.setLogingProperties(this.logger);
    }
    /**
     * 初始化
     */
    @FXML
    private void initialize(){
        new AutoHintTextField(queryTF);
        loadConfigure();
        Tab tab = this.tabPane.getTab("启动页");
        Button queryCert = new Button("计算");
        Label label = new Label("证书序列号计算器");
        Label resLabel = new Label("结果");
        TextField tf = new TextField();
        TextField res = new TextField();
        tf.setPromptText("请将16进制证书序列号粘贴到此处！");
        tf.setPrefWidth(400);
        res.setPrefWidth(500);
        label.setFont(Font.font(14));
        resLabel.setFont(Font.font(14));
        queryCert.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                if(tf.getText() != null){
                    String txt = tf.getText().trim().replaceAll(" ", "");
                    BigInteger i = new BigInteger(txt, 16);
                    res.setText(i.toString());
                }
            }
        });
        VBox vb = new VBox();
        HBox hb = new HBox();
        HBox res_hb = new HBox();
        hb.getChildren().add(label);
        hb.getChildren().add(tf);
        hb.getChildren().add(queryCert);
        label.setPadding(new Insets(3));
        resLabel.setPadding(new Insets(3));
        hb.setPadding(new Insets(20));
        hb.setSpacing(10);
        res_hb.setSpacing(45);
        hb.setAlignment(Pos.TOP_CENTER);
        res_hb.setAlignment(Pos.TOP_CENTER);
        res_hb.getChildren().add(resLabel);
        res_hb.getChildren().add(res);
        vb.getChildren().add(hb);
        vb.getChildren().add(res_hb);
        VBox.setMargin(res, new Insets(0, 100, 0 ,100));
        tab.setContent(vb);
    }

    /**
     * 查询按钮点击事件，与fxml中命名绑定
     * @param event 点击事件
     */
    @FXML
    private void queryAction(ActionEvent event){
        if(queryTF.getText() != null){
            query(queryTF.getText());
        }
    }

    @FXML
    private void showAbout(ActionEvent event){
        showAlert(Alert.AlertType.INFORMATION, null, "WgpSec Team \n 项目地址：\n https://github.com/wgpsec/fofa_viewer");
    }

    /**
     * 导出查询数据
     */
    @FXML
    private void exportAction(ActionEvent e) {
        if(tabPane.getCurrentTab().getText().equals("起始页")) return;  // 起始页无数据可导出
        ArrayList<String> list = this.tabPane.getList(tabPane.getCurrentTab());
        ArrayList<String> tmp = new ArrayList<>();
        for(String i : list){
            if(!i.startsWith("http")){
                tmp.add("http://" + i);
            }else{
                tmp.add(i);
            }
        }
        Stage stage = (Stage) rootLayout.getScene().getWindow();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择导出结果到文件...");
        try {
            File file = fileChooser.showSaveDialog(stage);
            if(file != null){
                BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                for(String i : tmp){
                    writer.write(i);
                    writer.newLine();
                }
                writer.close();
            }
        }catch (IOException ex){
            logger.log(Level.WARNING,ex.getMessage(), ex);
            showAlert(Alert.AlertType.ERROR, null, "导出失败，错误原因：" + ex.getMessage());
            return;
        }
        showAlert(Alert.AlertType.INFORMATION, null, "导出成功！");
    }

    /**
     * 处理查询结果
     */
    public void query(String text){
        text = text.trim();
        if(this.tabPane.isExistTab(text)){ // 若已存在同名Tab 则直接跳转，不查询
            this.tabPane.setCurrentTab(this.tabPane.getTab(text));
            return;
        }
        String tabTitle = text;
        if(checkHoneyPot.isSelected()){
            text = "(" + text + ") && (is_honeypot=false && is_fraud=false)";
            tabTitle = "(*)" + tabTitle; // 带有排除蜜罐的给tab设置标记
        }
        text = RequestHelper.encode(text);
        HashMap<String,String> result = this.helper.getHTML(this.client.getParam() + text);
        if(result.get("code").equals("200")){
            Tab tab = new Tab();
            ArrayList<String> dataList = new ArrayList<>();
            JSONObject obj = JSON.parseObject(result.get("msg"));
            if(obj.getBoolean("error")){
                showAlert(Alert.AlertType.ERROR, null, obj.getString("errmsg"));
                return;
            }
            HashMap<String, TableBean> list = new HashMap<>();
            HashMap<String, TableBean> tmp = new HashMap<>();
            JSONArray array = obj.getJSONArray("results");
            int count = 0;  // 默认排序
            for(int index=0; index < array.size(); index ++){
                JSONArray _array = array.getJSONArray(index);
                String host = _array.getString(0);
                String title = _array.getString(1);
                String ip = _array.getString(2);
                String domain = _array.getString(3);
                int port = Integer.parseInt(_array.getString(4));
                String protocol = _array.getString(5);
                String server = _array.getString(6);
                String _host = ip + ":" +port;
                TableBean b = new TableBean(++count, host, title, ip, domain, port, protocol, server);
                if(protocol.equals("")){
                    dataList.add(host);
                }
                if(host.startsWith("http")){
                    tmp.put(_host, b);
                }
                if(list.containsKey(_host) && protocol.equals("")){ //筛除http协议的端口出现两次的情况
                    count--;
                    b.num = list.get(_host).num;
                    list.remove(_host);
                }else if((list.containsKey(_host) || tmp.containsKey(_host)) && !protocol.equals("")){
                    count--;continue;
                }
                list.put(host, b);
            }
            tab.setText(tabTitle);
            this.tabPane.addTab(tab, dataList);
            BorderPane tablePane = new BorderPane();
            TableView<TableBean> view = new TableView<TableBean>(FXCollections.observableArrayList(list.values()));
            setTable(view);
            tablePane.setCenter(view);
            tab.setContent(tablePane);
            tabPane.setCurrentTab(tab);
        }else{
            showAlert(Alert.AlertType.ERROR, result.get("code"), result.get("msg"));
        }
    }

    /**
     * 从配置文件加载fofa认证信息
     */
    public void loadConfigure(){
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream("config.properties"));
            this.client = new FofaBean(properties.getProperty("email").trim(), properties.getProperty("key").trim());
            this.client.setSize(properties.getProperty("maxSize"));
        } catch (IOException e){
            showAlert(Alert.AlertType.ERROR, null, "缺少配置文件config.properties！");
            Platform.exit();  //结束进程
        }
    }

    /**
     * 表格配置
     * @param view
     */
    public void setTable(TableView<TableBean> view){
        TableColumn<TableBean, Integer> num = new TableColumn<>("序号");
        TableColumn<TableBean, String> host = new TableColumn<>("HOST");
        TableColumn<TableBean, String> title = new TableColumn<>("标题");
        TableColumn<TableBean, String> ip = new TableColumn<>("IP");
        TableColumn<TableBean, Integer> port = new TableColumn<>("端口");
        TableColumn<TableBean, String> domain = new TableColumn<>("域名");
        TableColumn<TableBean, String> protocol = new TableColumn<>("协议");
        TableColumn<TableBean, String> server = new TableColumn<>("Server指纹");
        List<TableColumn<TableBean,String>> list = new ArrayList<TableColumn<TableBean,String>>(){{
            add(host);add(title);add(ip);
        }};
        num.setCellValueFactory(param -> param.getValue().getNum().asObject());
        host.setCellValueFactory(param -> param.getValue().getHost());
        title.setCellValueFactory(param -> param.getValue().getTitle());
        ip.setCellValueFactory(param -> param.getValue().getIp());
        port.setCellValueFactory(param -> param.getValue().getPort().asObject());
        domain.setCellValueFactory(param -> param.getValue().getDomain());
        protocol.setCellValueFactory(param -> param.getValue().getProtocol());
        server.setCellValueFactory(param -> param.getValue().getServer());
        view.getColumns().add(num);
        view.getColumns().addAll(list);
        view.getColumns().add(port);
        view.getColumns().add(domain);
        view.getColumns().add(protocol);
        view.getColumns().add(server);
        view.setRowFactory(param -> {
            final TableRow<TableBean> row = new TableRow<>();
            // 设置表格右键菜单
            ContextMenu rowMenu = new ContextMenu();
            MenuItem copyLink = new MenuItem("复制链接");
            copyLink.setOnAction(event -> { //设置剪贴板
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                content.putString(row.getItem().host.getValue());
                clipboard.setContent(content);
            });
            MenuItem queryCSet = new MenuItem("查询对应C段资产");
            queryCSet.setOnAction(event -> {
                String ip1 = row.getItem().ip.getValue();
                String queryText = ip1.substring(0, ip1.lastIndexOf('.'));
                query("ip=\""+ queryText + ".1/24" + "\"");
            });
            MenuItem querySubdomain = new MenuItem("查询相关域名资产");
            querySubdomain.setOnAction(event -> {
                String domain1 = row.getItem().domain.getValue();
                if(!domain1.equals("")){
                    query("domain=\""+ domain1 + "\"");
                }
            });
            MenuItem favicon = new MenuItem("从fofa搜索favicon相关的资产");
            favicon.setOnAction(event -> {
                String url = row.getItem().host.getValue();
                if(!url.startsWith("http")){
                    url = "http://" + url;
                    if(url.endsWith("443")){
                        url = "https://" + url.substring(0, url.length()-4);
                    }
                }
                String link = helper.getLinkIcon(url); // 请求获取link中的favicon链接
                HashMap<String,String> res = null;
                if(link !=null){
                    res = helper.getImageFavicon(link);
                }else{
                    res = helper.getImageFavicon(url + "/favicon.ico");
                }
                if(res != null){
                    if(res.get("code").equals("error")){
                        showAlert(Alert.AlertType.ERROR, null, res.get("msg"));return;
                    }
                    query(res.get("msg"));
                    return;
                }
                showAlert(Alert.AlertType.ERROR, null, "该网站未提供favicon");
            });
            MenuItem cert = new MenuItem("从fofa搜索证书相关的资产");
            cert.setOnAction(event -> {
                String host1 = row.getItem().host.getValue();
                if(host1.startsWith("https")){
                    try {
                        String value = helper.getCertSerialNum(host1);
                        if(value != null){
                            query(value);
                        }
                    }catch (Exception e){
                        logger.log(Level.WARNING, e.getMessage(), e);
                    }
                }else{
                    showAlert(Alert.AlertType.WARNING, null, "请选中host中带有https的行再点击查询证书相关资产");
                }
            });
            rowMenu.getItems().addAll(copyLink, queryCSet, querySubdomain, favicon, cert);
            row.contextMenuProperty().bind(Bindings.when(row.emptyProperty()).then((ContextMenu) null).otherwise(rowMenu));
            // 双击行时使用默认浏览器打开
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (! row.isEmpty()) ) {
                    String url = row.getItem().host.getValue();
                    String protocol1 = row.getItem().protocol.getValue();
                    String domain1 = row.getItem().domain.getValue();
                    if(protocol1.startsWith("http") || !domain1.equals("") || url.startsWith("http")){
                        if(!url.startsWith("http")){
                            if(url.endsWith("443")){
                                url = "https://" + url.substring(0, url.length()-4);
                            }
                            url = "http://" + url;
                        }
                        java.net.URI uri = java.net.URI.create(url);
                        java.awt.Desktop dp = java.awt.Desktop.getDesktop();
                        if (dp.isSupported(java.awt.Desktop.Action.BROWSE)) {
                            // 获取系统默认浏览器打开链接
                            try {
                                dp.browse(uri);
                            } catch (IOException e) {
                                logger.log(Level.WARNING, e.getMessage(), e);
                            }
                        }
                    }
                }
            });
            return row;
        });
        view.getSortOrder().add(num);
    }

    /**
     * 对话框配置
     * @param type 对话框类型
     * @param header 对话框标题
     * @param content 对话框内容
     */
    public void showAlert(Alert.AlertType type, String header, String content){
        Alert alert = new Alert(type);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
