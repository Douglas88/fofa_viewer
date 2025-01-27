package org.fofaviewer.controls;

import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;
import org.fofaviewer.utils.RequestHelper;
import java.util.List;
import java.util.regex.Pattern;

public class AutoHintTextField {
    private TextField textField;
    private final static int LIST_SHOW_SIZE = 7;
    private final static int LIST_CELL_HEIGHT = 24;
    private ObservableList<String> showCacheDataList = FXCollections.observableArrayList();
    private final RequestHelper helper = RequestHelper.getInstance();
    private String clickedInput = "";
    /** 输入内容后显示的提示信息列表 */
    private ListView<String> autoTipList = new ListView<>();

    /** 输入内容后显示的pop */
    private final Popup popShowList = new Popup();

    public AutoHintTextField(TextField textField) {
        if (null == textField) {
            throw new RuntimeException("textField 不能为空");
        }
        this.textField = textField;
        popShowList.setAutoHide(true);
        popShowList.getContent().add(autoTipList);
        autoTipList.setItems(showCacheDataList);
        confListnenr();
    }

    public final Scene getScene() {
        return textField.getScene();
    }

    /**
     * 显示pop
     */
    public final void showTipPop() {
        autoTipList.setPrefWidth(textField.getWidth() - 3);
        if(showCacheDataList.size() < LIST_SHOW_SIZE) {
            autoTipList.setPrefHeight(showCacheDataList.size() * LIST_CELL_HEIGHT + 3);
        } else {
            autoTipList.setPrefHeight(LIST_SHOW_SIZE * LIST_CELL_HEIGHT + 3);
        }
        Window window = getWindow();
        Scene scene = getScene();
        Point2D fieldPosition = textField.localToScene(0, 0);
        popShowList.show(window, window.getX() + fieldPosition.getX() + scene.getX(),
                window.getY() + fieldPosition.getY() + scene.getY() + textField.getHeight());
        autoTipList.getSelectionModel().clearSelection();
        autoTipList.getFocusModel().focus(-1);
    }

    /**
     * 设置监听器
     */
    private void confListnenr() {
        PauseTransition pause = new PauseTransition(Duration.seconds(1)); // 延时1秒查询api
        textField.textProperty().addListener((observable, oldValue, newValue) -> {
            pause.setOnFinished(event -> updateCacheDataList(oldValue, newValue));
            pause.playFromStart();
        });

        autoTipList.setOnMouseClicked(event -> selectedItem());

        autoTipList.setOnKeyPressed(event -> {
            if(event.getCode() == KeyCode.ENTER) {
                selectedItem();
            }
        });
    }

    /**
     * 选中条目点击事件
     */
    private void selectedItem() {
        clickedInput = "app=\"" + autoTipList.getSelectionModel().getSelectedItem().split("--")[0] + "\"";
        textField.setText(clickedInput);
        textField.end();
        popShowList.hide();
    }

    /**
     * 输入内容改变触发事件
     * @param oldValue 旧值
     * @param newValue 新值
     */
    private void updateCacheDataList(String oldValue, String newValue){
        if(newValue.trim().equals("")){ // 内容为空时不查询
            showCacheDataList.clear();
            return;
        }
        if(newValue.trim().equals(oldValue)){ // 键入空格不查询
            showTipPop();
            return;
        }
        if(clickedInput.equals(newValue)){ // 消除点击条目后自动触发的bug
            return;
        }
        this.showCacheDataList.clear();
        List<String> data = helper.getTips(newValue);
        if(data != null && data.size() != 0){
            this.showCacheDataList.addAll(data);
            showTipPop();
        }
    }

    public final Window getWindow() {
        return getScene().getWindow();
    }
}
