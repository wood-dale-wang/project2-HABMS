package com.habms.client;

import java.io.IOException;
import java.util.Map;

/**
 * Controller that builds requests and delegates transport to {@link ClientService}.
 */
public class ClientController {
    private final ClientService service;

    public ClientController(ClientService service) {
        this.service = service;
    }

    public Map login(String username, String password) throws IOException {
        return service.send(Map.of("action", "login", "username", username, "password", password));
    }

    public Map register(String username, String password, String fullname, String idcard, String phone) throws IOException {
        return service.send(Map.of(
                "action", "register",
                "username", username,
                "password", password,
                "fullname", fullname == null ? "" : fullname,
                "idcard", idcard == null ? "" : idcard,
                "phone", phone == null ? "" : phone
        ));
    }

    public Map logout() throws IOException { return service.send(Map.of("action", "logout")); }

    public Map listDoctors() throws IOException { return service.send(Map.of("action", "list_doctors")); }

    public Map listAppts(int doctorId) throws IOException { return service.send(Map.of("action", "list_appts", "doctorId", doctorId)); }

    public Map book(int doctorId, String patientName, String time) throws IOException {
        return service.send(Map.of("action", "book", "doctorId", doctorId, "patientName", patientName, "time", time));
    }

    public Map listMyAppts() throws IOException { return service.send(Map.of("action", "list_my_appts")); }

    public Map cancel(int apptId) throws IOException { return service.send(Map.of("action", "cancel", "apptId", apptId)); }

    public Map searchName(String q) throws IOException { return service.send(Map.of("action", "search_name", "q", q)); }

    public Map searchDept(String q) throws IOException { return service.send(Map.of("action", "search_dept", "q", q)); }

    public Map addDoctor(String name, String dept, String info) throws IOException {
        return service.send(Map.of("action", "add_doctor", "name", name, "dept", dept, "info", info));
    }

    public Map updateDoctor(int id, String name, String dept, String info) throws IOException {
        return service.send(Map.of("action", "update_doctor", "id", id, "name", name, "dept", dept, "info", info));
    }

    public Map addSchedule(int doctorId, String start, String end, String note, int capacity) throws IOException {
        return service.send(Map.of("action", "add_schedule", "doctorId", doctorId, "start", start, "end", end, "note", note == null ? "" : note, "capacity", capacity));
    }

    public Map listSchedules(int doctorId) throws IOException { return service.send(Map.of("action", "list_schedules", "doctorId", doctorId)); }

    public Map importDoctorsXls(String base64Content) throws IOException {
        return service.send(Map.of("action", "import_doctors_xls", "content", base64Content));
    }

    public Map exportAppointmentsXls() throws IOException {
        return service.send(Map.of("action", "export_appointments_xls"));
    }

    public Map generateReportPdf() throws IOException {
        return service.send(Map.of("action", "generate_report_pdf"));
    }

    public Map updateAccount(String password, String fullname, String phone) throws IOException {
        return service.send(Map.of(
                "action", "update_account",
                "password", password,
                "fullname", fullname == null ? "" : fullname,
                "phone", phone == null ? "" : phone
        ));
    }

    public Map deleteAccount() throws IOException { return service.send(Map.of("action", "delete_account")); }
}
