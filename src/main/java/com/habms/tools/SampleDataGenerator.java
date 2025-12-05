package com.habms.tools;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SampleDataGenerator {
    public static void main(String[] args) throws Exception {
        String out = "sample.xlsx";
        if (args.length > 0) out = args[0];
        XSSFWorkbook wb = new XSSFWorkbook();
        // Doctors sheet
        Sheet sd = wb.createSheet("Doctors");
        Row hd = sd.createRow(0);
        hd.createCell(0).setCellValue("name");
        hd.createCell(1).setCellValue("dept");
        hd.createCell(2).setCellValue("info");
        Row r1 = sd.createRow(1); r1.createCell(0).setCellValue("王医生"); r1.createCell(1).setCellValue("内科"); r1.createCell(2).setCellValue("示例内科医生");
        Row r2 = sd.createRow(2); r2.createCell(0).setCellValue("李医生"); r2.createCell(1).setCellValue("外科"); r2.createCell(2).setCellValue("示例外科医生");

        // Schedules sheet
        Sheet ss = wb.createSheet("Schedules");
        Row hs = ss.createRow(0);
        hs.createCell(0).setCellValue("doctor");
        hs.createCell(1).setCellValue("start");
        hs.createCell(2).setCellValue("end");
        hs.createCell(3).setCellValue("capacity");
        hs.createCell(4).setCellValue("note");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        LocalDate d = LocalDate.now().plusDays(1);
        LocalDateTime s1 = d.atTime(9,0);
        LocalDateTime e1 = d.atTime(10,0);
        Row srow1 = ss.createRow(1);
        srow1.createCell(0).setCellValue("王医生");
        srow1.createCell(1).setCellValue(s1.format(fmt));
        srow1.createCell(2).setCellValue(e1.format(fmt));
        srow1.createCell(3).setCellValue(2);
        srow1.createCell(4).setCellValue("早上门诊");

        LocalDateTime s2 = d.atTime(10,0);
        LocalDateTime e2 = d.atTime(11,0);
        Row srow2 = ss.createRow(2);
        srow2.createCell(0).setCellValue("李医生");
        srow2.createCell(1).setCellValue(s2.format(fmt));
        srow2.createCell(2).setCellValue(e2.format(fmt));
        srow2.createCell(3).setCellValue(1);
        srow2.createCell(4).setCellValue("上午门诊");

        try (FileOutputStream fos = new FileOutputStream(out)) {
            wb.write(fos);
        }
        wb.close();
        System.out.println("生成示例 Excel: " + out);
    }
}
