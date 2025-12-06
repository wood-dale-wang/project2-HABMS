package com.habms.client;

import javax.swing.*;
import java.awt.*;

public class FormFactory {

    // create a simple single-label + field panel (vertical)
    public static JPanel singleFieldPanel(String label, JTextField field) {
        JPanel p = new JPanel(new GridLayout(0,1,6,6));
        p.add(new JLabel(label));
        p.add(field);
        return p;
    }

    // add doctor fields (name, dept, info) into provided two-column panel
    public static void addDoctorFields(JPanel panel, JTextField nameF, JTextField deptF, JTextField infoF) {
        panel.add(new JLabel("医生姓名")); panel.add(nameF);
        panel.add(new JLabel("科室")); panel.add(deptF);
        panel.add(new JLabel("简介")); panel.add(infoF);
    }

    // create registration fields into provided panel
    public static void addRegistrationFields(JPanel panel, JTextField usernameF, JPasswordField passwordF, JTextField fullnameF, JTextField idcardF, JTextField phoneF) {
        panel.add(new JLabel("用户名")); panel.add(usernameF);
        panel.add(new JLabel("密码")); panel.add(passwordF);
        panel.add(new JLabel("姓名")); panel.add(fullnameF);
        panel.add(new JLabel("身份证号（注册后不可修改）")); panel.add(idcardF);
        panel.add(new JLabel("电话")); panel.add(phoneF);
    }

    // create booking fields into provided panel
    public static void addBookingFields(JPanel panel, JTextField docIdField, JSpinner dateTimeSpinner, JTextField nameField) {
        panel.add(new JLabel("医生ID")); panel.add(docIdField);
        panel.add(new JLabel("预约时间")); panel.add(dateTimeSpinner);
        panel.add(new JLabel("病人姓名")); panel.add(nameField);
    }

    // create schedule fields into provided panel
    public static void addScheduleFields(JPanel panel, JTextField didField, JSpinner dateSpinner, JSpinner startSpinner, JSpinner endSpinner, JSpinner capacitySpinner, JTextField noteField) {
        panel.add(new JLabel("医生ID")); panel.add(didField);
        panel.add(new JLabel("日期")); panel.add(dateSpinner);
        panel.add(new JLabel("开始时间")); panel.add(startSpinner);
        panel.add(new JLabel("结束时间")); panel.add(endSpinner);
        panel.add(new JLabel("容量")); panel.add(capacitySpinner);
        panel.add(new JLabel("备注")); panel.add(noteField);
    }
}
