package model.Users;

import java.util.ArrayList;
import java.util.List;

public final class Roles {

    private Roles() { }
    public static final String VSISITOR = "Besucher";
    public static final String FRESHMAN = "Frischling";
    public static final String MEMBER = "Mitglied";
    public static final String ALDERMEN = "Vorstand";
    public static final String ADMIN = "Admin";

    public static List<String> getRoles() {
        List<String> list  = new ArrayList<>();
        list.add(VSISITOR);
        list.add(FRESHMAN);
        list.add(MEMBER);
        list.add(ALDERMEN);
        list.add(ADMIN);
        return list;
    }
    public static boolean hasRequiredRole(String userRole, String requiredRole) {
        List<String> roles = getRoles();
        int userIndex = roles.indexOf(userRole);
        int requiredIndex = roles.indexOf(requiredRole);
        if (userIndex == -1) return false;
        if (requiredIndex == -1) return false;
        return userIndex >= requiredIndex;
    }
}

