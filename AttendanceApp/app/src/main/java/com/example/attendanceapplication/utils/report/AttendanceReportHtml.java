package com.example.attendanceapplication.utils.report;

import com.example.attendanceapplication.models.AttendanceReport;
import com.example.attendanceapplication.models.AttendanceReport.ReportColumn;
import com.example.attendanceapplication.models.AttendanceReport.ReportRow;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/**
 * Sinh trang HTML tự chứa (CSS inline, không JS, không tài nguyên ngoài) từ
 * {@link AttendanceReport} để xem trước trong WebView. Bố cục và màu sắc bám theo
 * file Excel do {@link AttendanceReportXlsxExporter} tạo ra.
 */
public final class AttendanceReportHtml {

    private AttendanceReportHtml() {}

    private static final String CSS =
            "body{margin:12px;background:#fff;color:#000;"
            + "font-family:'Times New Roman',Times,'Noto Serif',serif;font-size:14px}"
            + ".sheet{display:inline-block}"
            + "table{border-collapse:collapse}"
            + ".nat{width:100%}.nat td{text-align:center;font-weight:bold;padding:1px 8px;white-space:nowrap}"
            + ".nat .i{font-style:italic}"
            + "h1{font-size:19px;text-align:center;margin:16px 0 2px}"
            + ".sem{text-align:center;font-weight:bold;margin-bottom:10px}"
            + ".info{margin-bottom:12px}.info td{padding:2px 6px;white-space:nowrap}"
            + ".info td.v{font-weight:bold;padding-right:40px}"
            + ".grid th,.grid td{border:1px solid #000;padding:2px 5px;text-align:center}"
            + ".grid th{background:#DDEBF7;font-weight:bold}"
            + ".grid th.d{padding:4px 2px;height:96px}"
            + ".grid th.d div{writing-mode:vertical-rl;transform:rotate(180deg);"
            + "white-space:nowrap;margin:0 auto}"
            + ".grid td.l{text-align:left;white-space:nowrap}"
            + ".grid td.V{background:#FFC7CE;color:#9C0006;font-weight:bold}"
            + ".grid td.M{background:#FFEB9C;color:#9C5700;font-weight:bold}"
            + ".grid td.s{background:#F2F2F2}"
            + ".grid td.low{color:#C00000;font-weight:bold}"
            + ".grid tr.t td{background:#F2F2F2;font-weight:bold;font-size:13px}"
            + ".grid tr.t td.lb{text-align:right;font-size:14px}"
            + ".grid tr.t td.nt{font-weight:normal;font-style:italic;font-size:12px}"
            + ".legend{font-style:italic;font-size:13px;margin-top:12px}"
            + ".sign{width:100%;margin-top:14px}.sign td{text-align:center;font-weight:bold;"
            + "vertical-align:top;padding:2px 8px}"
            + ".sign .p{font-weight:normal;font-style:italic}";

    public static String build(AttendanceReport report) {
        StringBuilder sb = new StringBuilder(16 * 1024);
        sb.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>")
                .append(CSS).append("</style></head><body><div class=\"sheet\">");

        // Quốc hiệu
        sb.append("<table class=\"nat\"><tr><td>HỌC VIỆN KỸ THUẬT MẬT MÃ</td>")
                .append("<td>CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM</td></tr>")
                .append("<tr><td>Khoa: ").append(AttendanceReport.PLACEHOLDER).append("</td>")
                .append("<td class=\"i\">Độc lập - Tự do - Hạnh phúc</td></tr></table>");

        // Tiêu đề
        sb.append("<h1>BẢNG THỐNG KÊ ĐIỂM DANH LỚP HỌC PHẦN</h1>")
                .append("<div class=\"sem\">").append(esc(report.getSemesterLabel())).append("</div>");

        // Thông tin lớp
        sb.append("<table class=\"info\">");
        infoRow(sb, "Học phần:", report.getClassName(), "Mã học phần:", report.getClassId());
        infoRow(sb, "Lớp học phần:", report.getClassDisplayName(), "Số TC:", AttendanceReport.PLACEHOLDER);
        infoRow(sb, "Giảng viên giảng dạy:", report.getTeacherName(), "Khóa:", AttendanceReport.PLACEHOLDER);
        infoRow(sb, "Tổng số SV:", String.valueOf(report.getRows().size()),
                "Tổng số buổi:", String.valueOf(report.getColumns().size()));
        infoRow(sb, "Thời gian:", report.getDateRange(), "Ngày xuất:", report.getExportDate());
        sb.append("</table>");

        appendGrid(sb, report);

        sb.append("<div class=\"legend\">Ký hiệu: x – Có mặt; M – Đi muộn (tính là có mặt); V – Vắng. ")
                .append("Tỷ lệ chuyên cần = (Có mặt + Đi muộn) / Tổng số buổi. ")
                .append("Tỷ lệ dưới 80% được tô đỏ.</div>");

        sb.append("<table class=\"sign\"><tr><td></td><td></td>")
                .append("<td class=\"p\">Hà Nội, ngày &nbsp;&nbsp;&nbsp; tháng &nbsp;&nbsp;&nbsp; năm &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</td></tr>")
                .append("<tr><td>GIẢNG VIÊN GIẢNG DẠY<br>(Ký, ghi rõ họ tên)</td>")
                .append("<td>XÁC NHẬN<br>CỦA LÃNH ĐẠO KHOA</td>")
                .append("<td>ĐƠN VỊ TIẾP NHẬN<br>(PHÒNG KT&amp;ĐBCLĐT)</td></tr></table>");

        sb.append("</div></body></html>");
        return sb.toString();
    }

    private static void appendGrid(StringBuilder sb, AttendanceReport report) {
        List<ReportColumn> cols = report.getColumns();
        int n = cols.size();

        sb.append("<table class=\"grid\"><thead><tr>")
                .append("<th rowspan=\"2\">STT</th>")
                .append("<th rowspan=\"2\">Mã Sinh Viên</th>")
                .append("<th rowspan=\"2\" colspan=\"2\">Họ và tên</th>")
                .append("<th colspan=\"").append(n).append("\">Điểm danh theo ngày học</th>")
                .append("<th colspan=\"4\">Tổng hợp</th>")
                .append("<th rowspan=\"2\" style=\"min-width:110px\">Ghi chú</th></tr><tr>");
        for (ReportColumn col : cols) {
            sb.append("<th class=\"d\"><div>").append(esc(col.getLabel())).append("</div></th>");
        }
        sb.append("<th>Có mặt</th><th>Đi muộn</th><th>Vắng</th><th>Tỷ lệ<br>chuyên cần</th></tr></thead><tbody>");

        for (ReportRow row : report.getRows()) {
            sb.append("<tr><td>").append(row.getStt()).append("</td>")
                    .append("<td>").append(esc(row.getStudentCode())).append("</td>")
                    .append("<td class=\"l\">").append(esc(row.getMiddleName())).append("</td>")
                    .append("<td class=\"l\">").append(esc(row.getFirstName())).append("</td>");
            for (char mark : row.getMarks()) {
                if (mark == AttendanceReport.MARK_PRESENT) sb.append("<td>x</td>");
                else sb.append("<td class=\"").append(mark).append("\">").append(mark).append("</td>");
            }
            sb.append("<td class=\"s\">").append(row.getPresentCount()).append("</td>")
                    .append("<td class=\"s\">").append(row.getLateCount()).append("</td>")
                    .append("<td class=\"s\">").append(row.getAbsentCount()).append("</td>")
                    .append("<td class=\"s").append(AttendanceReport.isLowRate(row.getRate()) ? " low" : "")
                    .append("\">").append(percent(row.getRate())).append("</td>")
                    .append("<td></td></tr>");
        }
        sb.append("</tbody><tfoot>");

        // Dòng tổng theo từng buổi + tổng cả lớp (khớp công thức trong file Excel)
        sb.append("<tr class=\"t\"><td class=\"lb\" colspan=\"4\">Số SV có mặt (x + M)</td>");
        for (int c = 0; c < n; c++) sb.append("<td>").append(report.getPresentForColumn(c)).append("</td>");
        sb.append("<td colspan=\"2\">").append(report.getTotalPresent()).append("</td><td></td>")
                .append("<td").append(AttendanceReport.isLowRate(report.getClassRate()) ? " class=\"low\"" : "")
                .append(">").append(percent(report.getClassRate())).append("</td>")
                .append("<td class=\"nt\">← Tỷ lệ chuyên cần cả lớp</td></tr>");

        sb.append("<tr class=\"t\"><td class=\"lb\" colspan=\"4\">Số SV vắng (V)</td>");
        for (int c = 0; c < n; c++) sb.append("<td>").append(report.getAbsentForColumn(c)).append("</td>");
        sb.append("<td colspan=\"3\">").append(report.getTotalAbsent()).append("</td><td></td><td></td></tr>");
        sb.append("</tfoot></table>");
    }

    private static void infoRow(StringBuilder sb, String l1, String v1, String l2, String v2) {
        sb.append("<tr><td>").append(esc(l1)).append("</td><td class=\"v\">").append(esc(v1))
                .append("</td><td>").append(esc(l2)).append("</td><td class=\"v\">").append(esc(v2))
                .append("</td></tr>");
    }

    /**
     * Định dạng giống "0%" của Excel: Excel cắt về 15 chữ số có nghĩa rồi mới làm tròn
     * nửa lên (vd 33/40 = 0.825 → 83%, trong khi Math.round(82.4999…) cho 82).
     */
    static String percent(Double rate) {
        if (rate == null) return "";
        BigDecimal pct = BigDecimal.valueOf(rate).round(new MathContext(15))
                .movePointRight(2).setScale(0, RoundingMode.HALF_UP);
        return pct.toPlainString() + "%";
    }

    static String esc(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '&': out.append("&amp;"); break;
                case '<': out.append("&lt;"); break;
                case '>': out.append("&gt;"); break;
                case '"': out.append("&quot;"); break;
                case '\'': out.append("&#39;"); break;
                default: out.append(ch);
            }
        }
        return out.toString();
    }
}
