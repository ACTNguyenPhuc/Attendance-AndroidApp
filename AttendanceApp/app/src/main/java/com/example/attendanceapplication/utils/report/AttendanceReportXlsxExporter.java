package com.example.attendanceapplication.utils.report;

import com.example.attendanceapplication.models.AttendanceReport;
import com.example.attendanceapplication.models.AttendanceReport.ReportColumn;
import com.example.attendanceapplication.models.AttendanceReport.ReportRow;

import org.dhatim.fastexcel.BorderStyle;
import org.dhatim.fastexcel.ConditionalFormattingExpressionRule;
import org.dhatim.fastexcel.DataValidationErrorStyle;
import org.dhatim.fastexcel.PaperSize;
import org.dhatim.fastexcel.StyleSetter;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Ghi {@link AttendanceReport} ra file .xlsx (FastExcel) theo bố cục template
 * "Template_Thong_ke_diem_danh_theo_ca.xlsx". Cột tổng hợp và dòng tổng dùng công thức
 * Excel 2007 (COUNTIF/COUNTA/SUM/IF) để giảng viên sửa x/M/V thì số liệu tự cập nhật.
 * Không phụ thuộc Android — chạy được trên mọi thread (nên gọi ở background).
 */
public final class AttendanceReportXlsxExporter {

    public static final String MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String SHEET_NAME = "ThongKeDiemDanh";

    private static final String FONT = "Times New Roman";
    private static final String FILL_HEADER = "DDEBF7";
    private static final String FILL_SUMMARY = "F2F2F2";
    private static final String FILL_ABSENT = "FFC7CE";
    private static final String FONT_ABSENT = "9C0006";
    private static final String FILL_LATE = "FFEB9C";
    private static final String FONT_LATE = "9C5700";
    private static final String FONT_LOW_RATE = "C00000";

    // Cột cố định (0-based): A=STT, B=Mã SV, C=Họ đệm, D=Tên, từ E là các cột ngày
    private static final int COL_STT = 0;
    private static final int COL_CODE = 1;
    private static final int COL_MIDDLE = 2;
    private static final int COL_FIRST = 3;
    private static final int COL_FIRST_DATE = 4;

    // Dòng cố định (0-based) — dòng 12-13 Excel là header bảng, dữ liệu bắt đầu ở dòng 14
    private static final int ROW_TITLE = 3;
    private static final int ROW_SEMESTER = 4;
    private static final int ROW_INFO = 5;
    private static final int ROW_HEAD_1 = 11;
    private static final int ROW_HEAD_2 = 12;
    private static final int ROW_FIRST_DATA = 13;

    private AttendanceReportXlsxExporter() {}

    /** Tên file: ThongKeDiemDanh_{classId}_{yyyyMMdd_HHmm}.xlsx, bỏ ký tự không hợp lệ. */
    public static String buildFileName(AttendanceReport report, Date now) {
        String id = report.getClassId() == null ? "" : report.getClassId();
        id = id.replaceAll("[\\\\/:*?\"<>|\\s]+", "_").replaceAll("^_+|_+$", "");
        if (id.isEmpty()) id = "Lop";
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(now);
        return "ThongKeDiemDanh_" + id + "_" + stamp + ".xlsx";
    }

    public static void write(AttendanceReport report, OutputStream out) throws IOException {
        Workbook wb = new Workbook(out, "AttendanceApp", "1.0");
        Worksheet ws = wb.newWorksheet(SHEET_NAME);
        new Layout(report, ws).write();
        wb.finish();
    }

    /** Tính vị trí các khối theo số buổi / số sinh viên rồi ghi từng phần. */
    private static final class Layout {
        private final AttendanceReport report;
        private final Worksheet ws;
        private final int n;            // số cột ngày
        private final int m;            // số sinh viên
        private final int lastDateCol;
        private final int colPresent, colLate, colAbsent, colRate, colNote;
        private final int lastDataRow;

        Layout(AttendanceReport report, Worksheet ws) {
            this.report = report;
            this.ws = ws;
            n = report.getColumns().size();
            m = report.getRows().size();
            lastDateCol = COL_FIRST_DATE + n - 1;
            colPresent = COL_FIRST_DATE + n;
            colLate = colPresent + 1;
            colAbsent = colPresent + 2;
            colRate = colPresent + 3;
            colNote = colPresent + 4;
            lastDataRow = ROW_FIRST_DATA + m - 1;
        }

        void write() throws IOException {
            writeColumnsAndPage();
            writeNationalHeader();
            writeTitle();
            writeClassInfo();
            writeTableHeader();
            writeStudentRows();
            writeTotalRows();
            writeFooter();
        }

        private void writeColumnsAndPage() {
            ws.width(COL_STT, 5);
            ws.width(COL_CODE, 11);
            ws.width(COL_MIDDLE, 17);
            ws.width(COL_FIRST, 8);
            for (int c = COL_FIRST_DATE; c <= lastDateCol; c++) ws.width(c, 5);
            ws.width(colPresent, 7.5);
            ws.width(colLate, 7.5);
            ws.width(colAbsent, 7.5);
            ws.width(colRate, 10);
            ws.width(colNote, 18);

            ws.rowHeight(0, 22);
            ws.rowHeight(1, 22);
            ws.rowHeight(2, 10);
            ws.rowHeight(ROW_TITLE, 28);
            ws.rowHeight(ROW_SEMESTER, 22);
            for (int r = ROW_INFO; r < ROW_INFO + 5; r++) ws.rowHeight(r, 20);
            ws.rowHeight(ROW_HEAD_1, 24);
            ws.rowHeight(ROW_HEAD_2, 70);
            for (int r = ROW_FIRST_DATA; r <= lastDataRow; r++) ws.rowHeight(r, 18);
            ws.rowHeight(lastDataRow + 1, 20);
            ws.rowHeight(lastDataRow + 2, 20);

            // Freeze tại ô đầu tiên của vùng điểm danh (E14)
            ws.freezePane(COL_FIRST_DATE, ROW_FIRST_DATA);
            // In: A4 ngang, vừa 1 trang chiều rộng, lặp header bảng mỗi trang
            ws.pageOrientation("landscape");
            ws.paperSize(PaperSize.A4_PAPER);
            ws.setFitToPage(true);
            ws.fitToWidth((short) 1);
            ws.fitToHeight((short) 0);
            ws.repeatRows(ROW_HEAD_1, ROW_HEAD_2);
            ws.leftMargin(0.4f);
            ws.rightMargin(0.4f);
            ws.topMargin(1.0f);
            ws.bottomMargin(1.0f);
            ws.headerMargin(0.5f);
            ws.footerMargin(0.5f);
        }

        private void writeNationalHeader() {
            // Khối trái A..F, khối phải G..cột cuối (luôn ≥ 4 cột vì có ít nhất 1 buổi)
            int leftEnd = 5;
            textBlock(0, 0, leftEnd, "HỌC VIỆN KỸ THUẬT MẬT MÃ", 13, true, false);
            textBlock(1, 0, leftEnd, "Khoa: " + AttendanceReport.PLACEHOLDER, 12, true, false);
            textBlock(0, leftEnd + 1, colNote, "CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM", 13, true, false);
            textBlock(1, leftEnd + 1, colNote, "Độc lập - Tự do - Hạnh phúc", 13, true, true);
        }

        private void writeTitle() {
            textBlock(ROW_TITLE, 0, colNote, "BẢNG THỐNG KÊ ĐIỂM DANH LỚP HỌC PHẦN", 16, true, false);
            textBlock(ROW_SEMESTER, 0, colNote, report.getSemesterLabel(), 12, true, false);
        }

        private void writeClassInfo() {
            // Bố cục giống template khi đủ rộng (≥ 8 buổi); lớp ít buổi thì dồn khối phải
            // sang các cột tổng hợp để nhãn/giá trị không bị bóp hẹp.
            int rightLabelStart, rightLabelEnd, rightValueStart, rightValueEnd;
            if (n >= 8) {
                rightValueEnd = colPresent;
                rightValueStart = colPresent - 2;
                rightLabelEnd = rightValueStart - 1;
                rightLabelStart = rightLabelEnd - 3;
            } else {
                rightLabelStart = colPresent;
                rightLabelEnd = colLate;
                rightValueStart = colAbsent;
                rightValueEnd = colNote;
            }
            int leftValueEnd = rightLabelStart - 1;

            String[][] left = {
                    {"Học phần:", nz(report.getClassName())},
                    {"Lớp học phần:", nz(report.getClassDisplayName())},
                    {"Giảng viên giảng dạy:", nz(report.getTeacherName())},
                    {"Tổng số SV:", null},   // công thức COUNTA bên dưới
                    {"Thời gian:", nz(report.getDateRange())},
            };
            String[][] right = {
                    {"Mã học phần:", nz(report.getClassId())},
                    {"Số TC:", AttendanceReport.PLACEHOLDER},
                    {"Khóa:", AttendanceReport.PLACEHOLDER},
                    {"Tổng số buổi:", null},  // số
                    {"Ngày xuất:", nz(report.getExportDate())},
            };
            for (int i = 0; i < 5; i++) {
                int r = ROW_INFO + i;
                infoCell(r, 0, 1, left[i][0], false);
                if (i == 3) {
                    ws.formula(r, 2, "COUNTA(" + ref(COL_CODE, ROW_FIRST_DATA) + ":"
                            + ref(COL_CODE, lastDataRow) + ")");
                    infoStyle(r, 2, leftValueEnd, true);
                } else {
                    infoCell(r, 2, leftValueEnd, left[i][1], true);
                }
                infoCell(r, rightLabelStart, rightLabelEnd, right[i][0], false);
                if (i == 3) {
                    ws.value(r, rightValueStart, n);
                    infoStyle(r, rightValueStart, rightValueEnd, true);
                } else {
                    infoCell(r, rightValueStart, rightValueEnd, right[i][1], true);
                }
            }
        }

        private void writeTableHeader() {
            headerCell(ROW_HEAD_1, COL_STT, ROW_HEAD_2, COL_STT, "STT", 0);
            headerCell(ROW_HEAD_1, COL_CODE, ROW_HEAD_2, COL_CODE, "Mã Sinh Viên", 0);
            headerCell(ROW_HEAD_1, COL_MIDDLE, ROW_HEAD_2, COL_FIRST, "Họ và tên", 0);
            headerCell(ROW_HEAD_1, COL_FIRST_DATE, ROW_HEAD_1, lastDateCol,
                    "Điểm danh theo ngày học", 0);
            List<ReportColumn> cols = report.getColumns();
            for (int i = 0; i < n; i++) {
                // Header ngày xoay dọc 90°
                headerCell(ROW_HEAD_2, COL_FIRST_DATE + i, ROW_HEAD_2, COL_FIRST_DATE + i,
                        cols.get(i).getLabel(), 90);
            }
            headerCell(ROW_HEAD_1, colPresent, ROW_HEAD_1, colRate, "Tổng hợp", 0);
            headerCell(ROW_HEAD_2, colPresent, ROW_HEAD_2, colPresent, "Có mặt", 0);
            headerCell(ROW_HEAD_2, colLate, ROW_HEAD_2, colLate, "Đi muộn", 0);
            headerCell(ROW_HEAD_2, colAbsent, ROW_HEAD_2, colAbsent, "Vắng", 0);
            headerCell(ROW_HEAD_2, colRate, ROW_HEAD_2, colRate, "Tỷ lệ chuyên cần", 0);
            headerCell(ROW_HEAD_1, colNote, ROW_HEAD_2, colNote, "Ghi chú", 0);
        }

        private void writeStudentRows() {
            List<ReportRow> rows = report.getRows();
            for (int i = 0; i < m; i++) {
                ReportRow row = rows.get(i);
                int r = ROW_FIRST_DATA + i;
                ws.value(r, COL_STT, row.getStt());
                ws.value(r, COL_CODE, nz(row.getStudentCode()));
                ws.value(r, COL_MIDDLE, nz(row.getMiddleName()));
                ws.value(r, COL_FIRST, nz(row.getFirstName()));
                char[] marks = row.getMarks();
                for (int c = 0; c < n; c++) {
                    ws.value(r, COL_FIRST_DATE + c, String.valueOf(marks[c]));
                }
                String marksRange = ref(COL_FIRST_DATE, r) + ":" + ref(lastDateCol, r);
                ws.formula(r, colPresent, "COUNTIF(" + marksRange + ",\"x\")");
                ws.formula(r, colLate, "COUNTIF(" + marksRange + ",\"M\")");
                ws.formula(r, colAbsent, "COUNTIF(" + marksRange + ",\"V\")");
                ws.formula(r, colRate, "IF(COUNTA(" + marksRange + ")=0,\"\",("
                        + ref(colPresent, r) + "+" + ref(colLate, r) + ")/COUNTA(" + marksRange + "))");
            }
            if (m == 0) return;

            body(ws.range(ROW_FIRST_DATA, COL_STT, lastDataRow, COL_CODE).style(), "center").set();
            body(ws.range(ROW_FIRST_DATA, COL_MIDDLE, lastDataRow, COL_FIRST).style(), "left").set();
            body(ws.range(ROW_FIRST_DATA, COL_FIRST_DATE, lastDataRow, lastDateCol).style(), "center").set();
            body(ws.range(ROW_FIRST_DATA, colPresent, lastDataRow, colAbsent).style(), "center")
                    .fillColor(FILL_SUMMARY).set();
            body(ws.range(ROW_FIRST_DATA, colRate, lastDataRow, colRate).style(), "center")
                    .fillColor(FILL_SUMMARY).format("0%").set();
            body(ws.range(ROW_FIRST_DATA, colNote, lastDataRow, colNote).style(), "left").set();

            // Chỉ cho chọn x / M / V trong vùng điểm danh
            ws.range(ROW_FIRST_DATA, COL_FIRST_DATE, lastDataRow, lastDateCol)
                    .validateWithListByFormula("\"x,M,V\"")
                    .allowBlank(true)
                    .showDropdown(true)
                    .showErrorMessage(true)
                    .errorStyle(DataValidationErrorStyle.STOP)
                    .errorTitle("Ký hiệu không hợp lệ")
                    .error("Chỉ nhập x (có mặt), M (đi muộn) hoặc V (vắng).");

            // Conditional formatting: V đỏ, M vàng, tỷ lệ < 80% chữ đỏ đậm
            String firstMark = ref(COL_FIRST_DATE, ROW_FIRST_DATA);
            ws.range(ROW_FIRST_DATA, COL_FIRST_DATE, lastDataRow, lastDateCol).style()
                    .fillColor(FILL_ABSENT).fontColor(FONT_ABSENT).bold()
                    .set(new ConditionalFormattingExpressionRule(firstMark + "=\"V\"", false));
            ws.range(ROW_FIRST_DATA, COL_FIRST_DATE, lastDataRow, lastDateCol).style()
                    .fillColor(FILL_LATE).fontColor(FONT_LATE).bold()
                    .set(new ConditionalFormattingExpressionRule(firstMark + "=\"M\"", false));
            // FastExcel (0.20.2) không escape XML trong công thức CF: ký tự '<' làm hỏng
            // sheet1.xml và Excel từ chối mở file. Vì vậy viết "ô có số và < 80%" bằng '>'
            // thay cho AND(T14<>"",T14<0.8) của template (tương đương về nghĩa).
            String firstRate = ref(colRate, ROW_FIRST_DATA);
            ws.range(ROW_FIRST_DATA, colRate, lastDataRow, colRate).style()
                    .fontColor(FONT_LOW_RATE).bold()
                    .set(new ConditionalFormattingExpressionRule(
                            "AND(ISNUMBER(" + firstRate + ")," + AttendanceReport.GOOD_RATE_THRESHOLD
                                    + ">" + firstRate + ")", false));
        }

        private void writeTotalRows() {
            int rPresent = lastDataRow + 1;
            int rAbsent = lastDataRow + 2;

            totalLabel(rPresent, "Số SV có mặt (x + M)");
            totalLabel(rAbsent, "Số SV vắng (V)");
            for (int c = COL_FIRST_DATE; c <= lastDateCol; c++) {
                String col = ref(c, ROW_FIRST_DATA) + ":" + ref(c, lastDataRow);
                ws.formula(rPresent, c, "COUNTIF(" + col + ",\"x\")+COUNTIF(" + col + ",\"M\")");
                ws.formula(rAbsent, c, "COUNTIF(" + col + ",\"V\")");
            }
            totals(ws.range(rPresent, COL_FIRST_DATE, rAbsent, colRate).style()).set();

            // Tổng có mặt cả lớp (gộp cột Có mặt + Đi muộn), tỷ lệ chuyên cần cả lớp
            ws.formula(rPresent, colPresent, "SUM(" + colRange(colPresent) + ")+SUM(" + colRange(colLate) + ")");
            mergeRange(rPresent, colPresent, rPresent, colLate);
            String marks = ref(COL_FIRST_DATE, ROW_FIRST_DATA) + ":" + ref(lastDateCol, lastDataRow);
            ws.formula(rPresent, colRate, "IF(COUNTA(" + marks + ")=0,\"\","
                    + ref(colPresent, rPresent) + "/COUNTA(" + marks + "))");
            totals(ws.style(rPresent, colRate)).format("0%").set();
            ws.value(rPresent, colNote, "← Tỷ lệ chuyên cần cả lớp");
            ws.style(rPresent, colNote).fontName(FONT).fontSize(10).italic()
                    .fillColor(FILL_SUMMARY).borderStyle(BorderStyle.THIN)
                    .horizontalAlignment("center").verticalAlignment("center").wrapText(true).set();
            ws.style(rAbsent, colNote).fillColor(FILL_SUMMARY).borderStyle(BorderStyle.THIN).set();

            // Tổng vắng cả lớp (gộp Có mặt..Vắng)
            ws.formula(rAbsent, colPresent, "SUM(" + colRange(colAbsent) + ")");
            mergeRange(rAbsent, colPresent, rAbsent, colAbsent);
        }

        private void writeFooter() {
            int rLegend = lastDataRow + 4;
            int rNote = lastDataRow + 5;
            int rPlace = lastDataRow + 7;
            int rSign1 = lastDataRow + 8;
            int rSign2 = lastDataRow + 9;

            footerText(rLegend, "Ký hiệu:  x – Có mặt;   M – Đi muộn (tính là có mặt);   V – Vắng.   "
                    + "Tỷ lệ chuyên cần = (Có mặt + Đi muộn) / Tổng số buổi. Tỷ lệ dưới 80% được tô đỏ.");
            footerText(rNote, "Ghi chú: Thầy cô vui lòng giữ nguyên cấu trúc của worksheet để đảm bảo "
                    + "tính chính xác khi nhập dữ liệu vào hệ thống!");

            // 3 khối ký tên: trái A..D, giữa các cột ngày, phải các cột tổng hợp.
            // Lớp ít buổi thì nới khối giữa sang 2 cột tổng hợp đầu cho đủ rộng.
            int midStart = COL_FIRST_DATE, midEnd, rightStart;
            if (n >= 6) { midEnd = lastDateCol; rightStart = colPresent; }
            else { midEnd = colLate; rightStart = colAbsent; }

            signText(rPlace, rightStart, colNote, "Hà Nội, ngày       tháng       năm         ", false, true);
            signText(rSign1, 0, 3, "GIẢNG VIÊN GIẢNG DẠY", true, false);
            signText(rSign2, 0, 3, "(Ký, ghi rõ họ tên)", true, false);
            signText(rSign1, midStart, midEnd, "XÁC NHẬN", true, false);
            signText(rSign2, midStart, midEnd, "CỦA LÃNH ĐẠO KHOA", true, false);
            signText(rSign1, rightStart, colNote, "ĐƠN VỊ TIẾP NHẬN", true, false);
            signText(rSign2, rightStart, colNote, "(PHÒNG KT&ĐBCLĐT)", true, false);
        }

        // ---------- helpers ----------

        private String colRange(int col) {
            return ref(col, ROW_FIRST_DATA) + ":" + ref(col, lastDataRow);
        }

        private void mergeRange(int r1, int c1, int r2, int c2) {
            if (r1 != r2 || c1 != c2) ws.range(r1, c1, r2, c2).merge();
        }

        private void textBlock(int r, int c1, int c2, String text, int size, boolean bold, boolean italic) {
            ws.value(r, c1, text);
            StyleSetter s = ws.range(r, c1, r, c2).style().fontName(FONT).fontSize(size)
                    .horizontalAlignment("center").verticalAlignment("center").wrapText(true);
            if (bold) s.bold();
            if (italic) s.italic();
            s.set();
            mergeRange(r, c1, r, c2);
        }

        private void infoCell(int r, int c1, int c2, String text, boolean bold) {
            ws.value(r, c1, text);
            infoStyle(r, c1, c2, bold);
        }

        private void infoStyle(int r, int c1, int c2, boolean bold) {
            StyleSetter s = ws.range(r, c1, r, c2).style().fontName(FONT).fontSize(12)
                    .horizontalAlignment("left").verticalAlignment("center");
            if (bold) s.bold();
            s.set();
            mergeRange(r, c1, r, c2);
        }

        private void headerCell(int r1, int c1, int r2, int c2, String text, int rotation) {
            ws.value(r1, c1, text);
            StyleSetter s = ws.range(r1, c1, r2, c2).style().fontName(FONT).fontSize(12).bold()
                    .fillColor(FILL_HEADER).borderStyle(BorderStyle.THIN)
                    .horizontalAlignment("center").verticalAlignment("center").wrapText(true);
            if (rotation != 0) s.rotation(rotation);
            s.set();
            mergeRange(r1, c1, r2, c2);
        }

        private StyleSetter body(StyleSetter s, String align) {
            return s.fontName(FONT).fontSize(12).borderStyle(BorderStyle.THIN)
                    .horizontalAlignment(align).verticalAlignment("center");
        }

        private StyleSetter totals(StyleSetter s) {
            return s.fontName(FONT).fontSize(11).bold().fillColor(FILL_SUMMARY)
                    .borderStyle(BorderStyle.THIN)
                    .horizontalAlignment("center").verticalAlignment("center").wrapText(true);
        }

        private void totalLabel(int r, String text) {
            ws.value(r, 0, text);
            ws.range(r, 0, r, COL_FIRST).style().fontName(FONT).fontSize(12).bold()
                    .fillColor(FILL_SUMMARY).borderStyle(BorderStyle.THIN)
                    .horizontalAlignment("right").verticalAlignment("center").set();
            mergeRange(r, 0, r, COL_FIRST);
        }

        private void footerText(int r, String text) {
            ws.value(r, 0, text);
            ws.range(r, 0, r, colNote).style().fontName(FONT).fontSize(11).italic()
                    .horizontalAlignment("left").verticalAlignment("center").set();
            mergeRange(r, 0, r, colNote);
        }

        private void signText(int r, int c1, int c2, String text, boolean bold, boolean italic) {
            ws.value(r, c1, text);
            StyleSetter s = ws.range(r, c1, r, c2).style().fontName(FONT).fontSize(12)
                    .horizontalAlignment("center").verticalAlignment("center").wrapText(true);
            if (bold) s.bold();
            if (italic) s.italic();
            s.set();
            mergeRange(r, c1, r, c2);
        }
    }

    /** Địa chỉ ô kiểu A1 từ chỉ số 0-based. */
    static String ref(int col, int row) {
        return colName(col) + (row + 1);
    }

    static String colName(int col) {
        StringBuilder sb = new StringBuilder();
        int c = col + 1;
        while (c > 0) {
            int rem = (c - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            c = (c - 1) / 26;
        }
        return sb.toString();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
