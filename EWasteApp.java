/*
 * E-Waste Collection & Recycling Management System   (IT19341 - Introduction to OOPS and Java)
 * Rajalakshmi Engineering College | Team: DEVESHWAR, K S SREE VISHAL, VIJAY KARTHIK S, SRIHARI S
 * 3 tiers: Swing screens -> DatabaseManager (logic + validation) -> MySQL (JDBC). All code is in this one file.
 */
import java.awt.*;
import java.awt.event.ActionListener;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;

/** Custom exception (inheritance): thrown for every validation / rule failure. */
class InvalidInputException extends Exception {
    InvalidInputException(String message) {
        super(message);
    }
}

/** Enum with behaviour: the request life-cycle rules live inside the enum. */
enum Status {
    PENDING, APPROVED, COLLECTED, RECYCLED, REJECTED;

    // PENDING -> APPROVED/REJECTED, APPROVED -> COLLECTED/REJECTED, COLLECTED -> RECYCLED. RECYCLED/REJECTED are final.
    boolean canMoveTo(Status next) {
        switch (this) {
            case PENDING:   return next == APPROVED || next == REJECTED;
            case APPROVED:  return next == COLLECTED || next == REJECTED;
            case COLLECTED: return next == RECYCLED;
            default:        return false;
        }
    }
}

/** Abstraction + Encapsulation: abstract parent class, private fields with getters. */
abstract class Person {
    private int id;
    private String fullName, username, phone;

    Person(int id, String fullName, String username, String phone) {
        this.id = id;
        this.fullName = fullName;
        this.username = username;
        this.phone = phone;
    }

    int getId() { return id; }
    String getFullName() { return fullName; }
    String getUsername() { return username; }
    String getPhone() { return phone; }

    abstract String getRole();        // every child says its role
    abstract void openDashboard();    // every child opens its own screen
}

/** Inheritance + overriding: a normal user who raises pickup requests. */
class User extends Person {
    User(int id, String fullName, String username, String phone) { super(id, fullName, username, phone); }

    @Override String getRole() { return "USER"; }
    @Override void openDashboard() { new UserDashboard(this).setVisible(true); }
}

/** Inheritance + overriding: the admin who approves, collects and recycles. */
class Admin extends Person {
    Admin(int id, String fullName, String username, String phone) { super(id, fullName, username, phone); }

    @Override String getRole() { return "ADMIN"; }
    @Override void openDashboard() { new AdminDashboard(this).setVisible(true); }
}

/** Class & object + encapsulation (one object = one row of pickup_requests) + constructor overloading. */
class PickupRequest {
    static final String[] CATEGORIES = {"Mobile", "Laptop", "Battery", "Charger", "Television", "Other"};

    private int id, quantity;
    private String username, itemName, category, address, pickupDate;   // pickupDate is yyyy-MM-dd
    private Status status;

    // constructor 1: a new request typed by the user (no id yet, always PENDING)
    PickupRequest(String username, String itemName, String category, int quantity, String address, String pickupDate) {
        this(0, username, itemName, category, quantity, address, pickupDate, Status.PENDING);
    }

    // constructor 2 (overloading): a request loaded from the database
    PickupRequest(int id, String username, String itemName, String category, int quantity,
                  String address, String pickupDate, Status status) {
        this.id = id;
        this.username = username;
        this.itemName = itemName;
        this.category = category;
        this.quantity = quantity;
        this.address = address;
        this.pickupDate = pickupDate;
        this.status = status;
    }

    int getId() { return id; }
    String getUsername() { return username; }
    String getItemName() { return itemName; }
    String getCategory() { return category; }
    int getQuantity() { return quantity; }
    String getAddress() { return address; }
    String getPickupDate() { return pickupDate; }
    Status getStatus() { return status; }

    /** Default recycling method per category (pre-fills the Record Recycling form). */
    static String suggestMethod(String category) {
        switch (category) {
            case "Mobile":     return "Component refurbishing and metal recovery";
            case "Laptop":     return "Dismantling and precious-metal recovery";
            case "Battery":    return "Hazardous waste neutralisation and lithium recovery";
            case "Charger":    return "Copper extraction and plastic shredding";
            case "Television": return "Dismantling and glass/metal separation";
            default:           return "General e-waste shredding and sorting";
        }
    }
}

/** Interface (abstraction): the request operations; DatabaseManager supplies the code. */
interface RequestOperations {
    void addRequest(PickupRequest r) throws InvalidInputException, SQLException;
    List<PickupRequest> getRequestsOfUser(String username) throws SQLException;
    List<PickupRequest> getAllRequests() throws SQLException;
    void updateStatus(int requestId, Status newStatus) throws InvalidInputException, SQLException;
    void addRecyclingRecord(int requestId, int qty, String method, String remarks) throws InvalidInputException, SQLException;
}

/** JDBC + interface implementation. Credentials are private (encapsulation). Uses try-with-resources everywhere. */
class DatabaseManager implements RequestOperations {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/ewaste_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String DB_USER = "ewaste";
    private static final String DB_PASS = "ewaste123";

    private static final String SELECT_REQUESTS =
        "SELECT r.request_id, u.username, r.item_name, r.category, r.quantity, r.address, r.pickup_date, r.status "
        + "FROM pickup_requests r JOIN users u ON r.user_id = u.user_id ";

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");   // driver comes from lib\mysql-connector-j-*.jar
        } catch (ClassNotFoundException e) {
            System.out.println("MySQL driver jar not found - copy it into the lib folder.");
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    // helper: runs one INSERT / UPDATE with the given values
    private void execute(String sql, Object... values) throws SQLException {
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                ps.setObject(i + 1, values[i]);
            }
            ps.executeUpdate();
        }
    }

    /** Polymorphism: returns a Person reference that is really a User or an Admin object (null = wrong login). */
    Person login(String username, String password) throws SQLException {
        String sql = "SELECT user_id, full_name, phone, role FROM users WHERE username = ? AND password = ?";
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                if (rs.getString("role").equals("ADMIN")) {
                    return new Admin(rs.getInt("user_id"), rs.getString("full_name"), username, rs.getString("phone"));
                }
                return new User(rs.getInt("user_id"), rs.getString("full_name"), username, rs.getString("phone"));
            }
        }
    }

    void registerUser(String fullName, String username, String password, String phone) throws InvalidInputException, SQLException {
        if (fullName.isEmpty() || username.isEmpty() || password.isEmpty()) throw new InvalidInputException("Full name, username and password must not be empty.");
        if (username.length() < 3) throw new InvalidInputException("Username must have at least 3 characters.");
        if (password.length() < 4) throw new InvalidInputException("Password must have at least 4 characters.");
        if (!phone.matches("\\d{10}")) throw new InvalidInputException("Phone number must be exactly 10 digits.");
        try {
            execute("INSERT INTO users (full_name, username, password, phone, role) VALUES (?, ?, ?, ?, 'USER')", fullName, username, password, phone);
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new InvalidInputException("Username '" + username + "' is already taken. Please choose another.");
        }
    }

    @Override
    public void addRequest(PickupRequest r) throws InvalidInputException, SQLException {
        if (r.getItemName().isEmpty()) throw new InvalidInputException("Item name must not be empty.");
        if (r.getQuantity() < 1 || r.getQuantity() > 100) throw new InvalidInputException("Quantity must be between 1 and 100 (you entered " + r.getQuantity() + ").");
        if (r.getAddress().isEmpty()) throw new InvalidInputException("Pickup address must not be empty.");
        LocalDate date;
        try {
            date = LocalDate.parse(r.getPickupDate());
        } catch (Exception e) {
            throw new InvalidInputException("Pickup date must be in yyyy-MM-dd format, e.g. " + LocalDate.now().plusDays(3));
        }
        if (date.isBefore(LocalDate.now())) throw new InvalidInputException("Pickup date cannot be in the past (" + date + ").");
        execute("INSERT INTO pickup_requests (user_id, item_name, category, quantity, address, pickup_date) "
                + "VALUES ((SELECT user_id FROM users WHERE username = ?), ?, ?, ?, ?, ?)",
                r.getUsername(), r.getItemName(), r.getCategory(), r.getQuantity(), r.getAddress(), Date.valueOf(date));
    }

    @Override
    public List<PickupRequest> getRequestsOfUser(String username) throws SQLException {
        return readRequests(SELECT_REQUESTS + "WHERE u.username = ? ORDER BY r.request_id DESC", username);
    }

    @Override
    public List<PickupRequest> getAllRequests() throws SQLException {
        return readRequests(SELECT_REQUESTS + "ORDER BY r.request_id DESC", null);
    }

    // helper for both getters above (param is null when there is no WHERE condition)
    private List<PickupRequest> readRequests(String sql, String param) throws SQLException {
        List<PickupRequest> list = new ArrayList<>();
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            if (param != null) ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new PickupRequest(rs.getInt("request_id"), rs.getString("username"), rs.getString("item_name"),
                            rs.getString("category"), rs.getInt("quantity"), rs.getString("address"),
                            rs.getString("pickup_date"), Status.valueOf(rs.getString("status"))));
                }
            }
        }
        return list;
    }

    @Override
    public void updateStatus(int requestId, Status newStatus) throws InvalidInputException, SQLException {
        Status current = findRequest(requestId).getStatus();
        if (!current.canMoveTo(newStatus)) throw new InvalidInputException("Invalid status change: " + current + " -> " + newStatus + " is not allowed.");
        if (newStatus == Status.RECYCLED) throw new InvalidInputException("Use the 'Record Recycling' button to mark a request as RECYCLED.");
        execute("UPDATE pickup_requests SET status = ? WHERE request_id = ?", newStatus.name(), requestId);
    }

    @Override
    public void addRecyclingRecord(int requestId, int qty, String method, String remarks) throws InvalidInputException, SQLException {
        PickupRequest r = findRequest(requestId);
        if (!r.getStatus().canMoveTo(Status.RECYCLED)) throw new InvalidInputException("Only COLLECTED requests can be recycled (this one is " + r.getStatus() + ").");
        if (qty < 1 || qty > r.getQuantity()) throw new InvalidInputException("Recycled quantity must be between 1 and " + r.getQuantity() + ".");
        if (method.isEmpty()) throw new InvalidInputException("Recycling method must not be empty.");
        try (Connection con = connect()) {
            con.setAutoCommit(false);   // transaction: insert the record and change the status together
            try (PreparedStatement ins = con.prepareStatement("INSERT INTO recycling_records "
                         + "(request_id, recycled_quantity, recycling_method, recycling_date, remarks) VALUES (?, ?, ?, CURDATE(), ?)");
                 PreparedStatement upd = con.prepareStatement("UPDATE pickup_requests SET status = 'RECYCLED' WHERE request_id = ?")) {
                ins.setInt(1, requestId);
                ins.setInt(2, qty);
                ins.setString(3, method);
                ins.setString(4, remarks);
                ins.executeUpdate();
                upd.setInt(1, requestId);
                upd.executeUpdate();
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    // helper: one request by id (error if it does not exist)
    private PickupRequest findRequest(int requestId) throws InvalidInputException, SQLException {
        List<PickupRequest> found = readRequests(SELECT_REQUESTS + "WHERE r.request_id = ?", "" + requestId);
        if (found.isEmpty()) throw new InvalidInputException("Request #" + requestId + " does not exist.");
        return found.get(0);
    }

    /** Summary numbers using GROUP BY. Pass a username for one user, or null for everything (admin). */
    Map<String, Integer> countByStatus(String username) throws SQLException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Status s : Status.values()) counts.put(s.name(), 0);
        String sql = "SELECT r.status, COUNT(*) FROM pickup_requests r JOIN users u ON r.user_id = u.user_id "
                   + "WHERE ? IS NULL OR u.username = ? GROUP BY r.status";
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, username);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) counts.put(rs.getString(1), rs.getInt(2));
            }
        }
        return counts;
    }
}

/** Colours and small helper methods (green eco theme) so every screen looks the same. */
class Theme {
    static final Color GREEN = new Color(0x1E8E5A), DARK = new Color(0x0E5F3F), TEAL = new Color(0x14919B);
    static final Color BG = new Color(0xF1F6F3), MUTED = new Color(0x6A7C73), BORDER = new Color(0xD3E0D9);
    static final Font BOLD = new Font("Segoe UI", Font.BOLD, 14);

    static Color statusColor(String status) {
        switch (status) {
            case "PENDING":   return new Color(0xE69B1E);
            case "APPROVED":  return new Color(0x2F7FD1);
            case "COLLECTED": return new Color(0x7B61C9);
            case "RECYCLED":  return GREEN;
            default:          return new Color(0xD64545);
        }
    }

    /** Method overloading (1): information message. */
    static void showMessage(Component parent, String message) {
        showMessage(parent, message, false);
    }

    /** Method overloading (2): same name, extra parameter -> error message. */
    static void showMessage(Component parent, String message, boolean isError) {
        JOptionPane.showMessageDialog(parent, message, isError ? "Error" : "E-Waste System",
                isError ? JOptionPane.ERROR_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
    }

    /** One place that turns any exception into a friendly error message. */
    static void showError(Component parent, Exception ex) {
        if (ex instanceof SQLException) showMessage(parent, "Database error: " + ex.getMessage(), true);
        else if (ex instanceof NumberFormatException) showMessage(parent, "Quantity must be a whole number.", true);
        else showMessage(parent, ex.getMessage(), true);   // InvalidInputException: our own message
    }

    static JButton button(String text, Color color) {
        JButton b = new JButton(text);
        b.setBackground(color);
        b.setForeground(Color.WHITE);
        b.setFont(BOLD);
        b.setFocusPainted(false);
        b.setMargin(new Insets(9, 18, 9, 18));
        return b;
    }

    static <T extends JTextField> T field(T f) {
        f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), new EmptyBorder(6, 10, 6, 10)));
        return f;
    }

    /** A transparent row of components (used for toolbars). */
    static JPanel row(int align, int gap, Component... items) {
        JPanel p = new JPanel(new FlowLayout(align, gap, 4));
        p.setOpaque(false);
        for (Component c : items) p.add(c);
        return p;
    }

    /** Green header bar: title on the left, logged-in user + Logout on the right. */
    static JPanel header(String subtitle, String userText, ActionListener onLogout) {
        JPanel bar = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                ((Graphics2D) g).setPaint(new GradientPaint(0, 0, DARK, getWidth(), 0, TEAL));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        bar.setBorder(new EmptyBorder(14, 22, 14, 22));
        JLabel title = new JLabel("<html><span style='font-size:18px'><b>E-Waste Collection & Recycling</b></span><br>" + subtitle + "</html>");
        JLabel user = new JLabel(userText);
        title.setForeground(Color.WHITE);
        user.setForeground(Color.WHITE);
        user.setFont(BOLD);
        JButton logout = button("Logout", new Color(0x0A4630));
        logout.addActionListener(onLogout);
        bar.add(title, BorderLayout.WEST);
        bar.add(row(FlowLayout.RIGHT, 14, user, logout), BorderLayout.EAST);
        return bar;
    }

    /** White summary card with a coloured number. */
    static JPanel card(String title, JLabel value, Color accent) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 6, 0, 0, accent),
                BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), new EmptyBorder(10, 14, 10, 14))));
        JLabel t = new JLabel(title);
        t.setForeground(MUTED);
        value.setFont(new Font("Segoe UI", Font.BOLD, 30));
        value.setForeground(accent);
        card.add(t, BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        return card;
    }

    /** Read-only table; the last column is the status badge. */
    static JTable table(String... columns) {
        JTable t = new JTable(new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        });
        t.setRowHeight(32);
        t.setShowGrid(false);
        t.setIntercellSpacing(new Dimension(0, 0));
        t.getTableHeader().setPreferredSize(new Dimension(0, 34));
        ((DefaultTableCellRenderer) t.getTableHeader().getDefaultRenderer()).setHorizontalAlignment(SwingConstants.LEFT);
        t.getColumnModel().getColumn(columns.length - 1).setCellRenderer(new StatusRenderer());
        return t;
    }

    /** Page layout: summary cards on top, toolbar + table below, inside a padded green-grey background. */
    static JPanel page(JPanel cards, JComponent top, JTable table) {
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(BORDER));
        sp.getViewport().setBackground(Color.WHITE);
        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(top, BorderLayout.NORTH);
        center.add(sp, BorderLayout.CENTER);
        JPanel body = new JPanel(new BorderLayout(0, 16));
        body.setBackground(BG);
        body.setBorder(new EmptyBorder(18, 22, 18, 22));
        body.add(cards, BorderLayout.NORTH);
        body.add(center, BorderLayout.CENTER);
        return body;
    }

    /** Adds "label : component" as one row of a GridBagLayout form. */
    static void addRow(JPanel form, int row, String label, JComponent field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(6, 4, 6, 8);
        c.anchor = GridBagConstraints.WEST;
        JLabel l = new JLabel(label);
        l.setFont(BOLD);
        form.add(l, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, c);
    }

    /** Builds a whole form from pairs: "Label", component, "Label", component ... */
    static JPanel form(Object... pairs) {
        JPanel form = new JPanel(new GridBagLayout());
        for (int i = 0; i < pairs.length; i += 2) addRow(form, i / 2, (String) pairs[i], (JComponent) pairs[i + 1]);
        return form;
    }

    /** Modal dialog = form in the middle + one action button at the bottom right. */
    static JDialog dialog(Window owner, String title, JPanel form, JButton okButton) {
        JDialog d = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        form.setBorder(new EmptyBorder(20, 24, 10, 24));
        d.add(form, BorderLayout.CENTER);
        d.add(row(FlowLayout.RIGHT, 16, okButton), BorderLayout.SOUTH);
        d.pack();
        d.setLocationRelativeTo(owner);
        return d;
    }
}

/** Inheritance + overriding: draws a table cell as a striped background plus a rounded, coloured status badge. */
class StatusRenderer extends DefaultTableCellRenderer {
    private String status = "";
    private Color rowColor = Color.WHITE;

    @Override
    public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int col) {
        status = String.valueOf(v);
        rowColor = sel ? t.getSelectionBackground() : row % 2 == 0 ? Color.WHITE : new Color(0xF6FAF8);
        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(rowColor);
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
        int y = (getHeight() - 22) / 2;
        g2.setColor(Theme.statusColor(status));
        g2.fillRoundRect(8, y, g2.getFontMetrics().stringWidth(status) + 24, 22, 22, 22);
        g2.setColor(Color.WHITE);
        g2.drawString(status, 20, y + 16);
        g2.dispose();
    }
}

/** Inheritance (a screen "is a" JFrame) + polymorphism (person.openDashboard()). Login and Register. */
class LoginScreen extends JFrame {
    private DatabaseManager db = new DatabaseManager();
    private JTextField userField = Theme.field(new JTextField(18));
    private JPasswordField passField = Theme.field(new JPasswordField(18));

    LoginScreen() {
        super("E-Waste Management System - Login");
        setSize(940, 580);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        // left: green brand panel
        JPanel brand = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                ((Graphics2D) g).setPaint(new GradientPaint(0, 0, Theme.DARK, getWidth(), getHeight(), Theme.TEAL));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        JLabel text = new JLabel("<html><div style='width:300px'><span style='font-size:34px'><b>E-Waste</b></span><br>"
                + "<span style='font-size:18px'>Collection &amp; Recycling<br>Management System</span><br><br>"
                + "<span style='font-size:14px'>&bull; Schedule e-waste pickups from home<br>&bull; Track every request till RECYCLED<br>"
                + "&bull; Help the planet, one device at a time<br><br><br>Rajalakshmi Engineering College<br>B.Tech IT | IT19341</span></div></html>");
        text.setForeground(Color.WHITE);
        brand.add(text);
        JPanel root = new JPanel(new GridLayout(1, 2));
        root.add(brand);
        root.add(buildLoginForm());
        add(root, BorderLayout.CENTER);
        JLabel bar = new JLabel("  Database: MySQL (ewaste_db)  |  IT19341 - OOPS and Java");
        bar.setForeground(Theme.MUTED);
        bar.setBorder(new EmptyBorder(6, 8, 6, 8));
        add(bar, BorderLayout.SOUTH);
    }

    // right: white login card
    private JPanel buildLoginForm() {
        JLabel welcome = new JLabel("Welcome back");
        welcome.setFont(new Font("Segoe UI", Font.BOLD, 24));
        welcome.setForeground(Theme.DARK);
        welcome.setBorder(new EmptyBorder(0, 0, 0, 8));
        JLabel sub = new JLabel("Login to continue");
        JLabel hint = new JLabel("<html><small>Demo: admin / admin123 &nbsp;|&nbsp; vishal / vishal123</small></html>");
        sub.setForeground(Theme.MUTED);
        hint.setForeground(Theme.MUTED);
        JButton loginBtn = Theme.button("Login", Theme.GREEN);
        JButton registerBtn = Theme.button("Register", Theme.TEAL);
        loginBtn.addActionListener(e -> doLogin());
        registerBtn.addActionListener(e -> buildRegisterDialog().setVisible(true));
        JPanel buttons = new JPanel(new GridLayout(1, 2, 10, 0));
        buttons.setOpaque(false);
        buttons.add(loginBtn);
        buttons.add(registerBtn);
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BORDER), new EmptyBorder(26, 30, 26, 30)));
        JPanel fields = Theme.form("Username", userField, "Password", passField);
        fields.setOpaque(false);
        for (Component c : new Component[]{welcome, sub, Box.createVerticalStrut(14), fields, Box.createVerticalStrut(16), buttons,
                Box.createVerticalStrut(10), hint}) {
            ((JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(c);
        }
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBackground(Theme.BG);
        outer.add(card);
        getRootPane().setDefaultButton(loginBtn);
        return outer;
    }

    private void doLogin() {
        try {
            Person p = db.login(userField.getText().trim(), new String(passField.getPassword()));
            if (p == null) {
                Theme.showMessage(this, "Invalid username or password.", true);
                return;
            }
            dispose();
            p.openDashboard();   // User -> UserDashboard, Admin -> AdminDashboard (runtime polymorphism)
        } catch (SQLException ex) {
            Theme.showError(this, ex);
        }
    }

    /** Builds (does not show) the registration dialog. */
    JDialog buildRegisterDialog() {
        JTextField name = Theme.field(new JTextField(20)), user = Theme.field(new JTextField(20));
        JTextField phone = Theme.field(new JTextField(20));
        JPasswordField pass = Theme.field(new JPasswordField(20));
        JPanel form = Theme.form("Full name", name, "Username", user, "Password", pass, "Phone (10 digits)", phone);
        JButton save = Theme.button("Create Account", Theme.GREEN);
        save.addActionListener(e -> {
            Window d = SwingUtilities.getWindowAncestor(save);
            try {
                db.registerUser(name.getText().trim(), user.getText().trim(), new String(pass.getPassword()), phone.getText().trim());
                Theme.showMessage(d, "Account created! You can now login as '" + user.getText().trim() + "'.");
                userField.setText(user.getText().trim());
                d.dispose();
            } catch (InvalidInputException | SQLException ex) {
                Theme.showError(d, ex);
            }
        });
        return Theme.dialog(this, "Register New User", form, save);
    }
}

/** Screen for a normal user: summary cards, my requests, new request form. */
class UserDashboard extends JFrame {
    private Person user;
    private DatabaseManager db = new DatabaseManager();
    private JLabel totalLbl = new JLabel("0"), pendingLbl = new JLabel("0"), progressLbl = new JLabel("0"), recycledLbl = new JLabel("0");
    private JTable table = Theme.table("ID", "Item", "Category", "Qty", "Pickup Date", "Address", "Status");

    UserDashboard(Person user) {
        super("E-Waste Management - My Dashboard");
        this.user = user;
        setSize(1100, 680);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        add(Theme.header("User Dashboard", user.getFullName() + "  (" + user.getRole() + ")", e -> {
            dispose();
            new LoginScreen().setVisible(true);
        }), BorderLayout.NORTH);
        JPanel cards = new JPanel(new GridLayout(1, 4, 16, 0));
        cards.setOpaque(false);
        cards.add(Theme.card("Total Requests", totalLbl, Theme.TEAL));
        cards.add(Theme.card("Pending", pendingLbl, Theme.statusColor("PENDING")));
        cards.add(Theme.card("In Progress", progressLbl, Theme.statusColor("APPROVED")));
        cards.add(Theme.card("Recycled", recycledLbl, Theme.GREEN));
        JLabel heading = new JLabel("My Requests");
        heading.setFont(new Font("Segoe UI", Font.BOLD, 18));
        heading.setForeground(Theme.DARK);
        JButton newBtn = Theme.button("+ New Pickup Request", Theme.GREEN);
        JButton refreshBtn = Theme.button("Refresh", Theme.TEAL);
        newBtn.addActionListener(e -> {
            buildRequestDialog().setVisible(true);
            loadData();
        });
        refreshBtn.addActionListener(e -> loadData());
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(heading, BorderLayout.WEST);
        top.add(Theme.row(FlowLayout.RIGHT, 10, refreshBtn, newBtn), BorderLayout.EAST);
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(3).setMaxWidth(60);
        add(Theme.page(cards, top, table), BorderLayout.CENTER);
        loadData();
    }

    void loadData() {
        try {
            DefaultTableModel model = (DefaultTableModel) table.getModel();
            model.setRowCount(0);
            for (PickupRequest r : db.getRequestsOfUser(user.getUsername())) {
                model.addRow(new Object[]{r.getId(), r.getItemName(), r.getCategory(), r.getQuantity(),
                        r.getPickupDate(), r.getAddress(), r.getStatus().name()});
            }
            Map<String, Integer> c = db.countByStatus(user.getUsername());
            int total = 0;
            for (int n : c.values()) total += n;
            totalLbl.setText("" + total);
            pendingLbl.setText("" + c.get("PENDING"));
            progressLbl.setText("" + (c.get("APPROVED") + c.get("COLLECTED")));
            recycledLbl.setText("" + c.get("RECYCLED"));
        } catch (SQLException ex) {
            Theme.showError(this, ex);
        }
    }

    /** Builds (does not show) the "New Pickup Request" form. */
    JDialog buildRequestDialog() {
        JTextField item = Theme.field(new JTextField(22)), qty = Theme.field(new JTextField("1", 22));
        JTextField address = Theme.field(new JTextField(22));
        JTextField date = Theme.field(new JTextField(LocalDate.now().plusDays(3).toString(), 22));
        JComboBox<String> category = new JComboBox<>(PickupRequest.CATEGORIES);
        JPanel form = Theme.form("Item name", item, "Category", category, "Quantity (1-100)", qty,
                "Pickup address", address, "Pickup date (yyyy-MM-dd)", date);
        JButton submit = Theme.button("Submit Request", Theme.GREEN);
        submit.addActionListener(e -> {
            Window d = SwingUtilities.getWindowAncestor(submit);
            try {
                int q = Integer.parseInt(qty.getText().trim());
                db.addRequest(new PickupRequest(user.getUsername(), item.getText().trim(), (String) category.getSelectedItem(),
                        q, address.getText().trim(), date.getText().trim()));
                Theme.showMessage(d, "Request submitted! Status: PENDING");
                d.dispose();
            } catch (InvalidInputException | SQLException | NumberFormatException ex) {
                Theme.showError(d, ex);      // shows the message of our custom exception
            }
        });
        return Theme.dialog(this, "New Pickup Request", form, submit);
    }
}

/** Screen for the admin: all requests, filter, status update, recycling, summary counts. */
class AdminDashboard extends JFrame {
    private DatabaseManager db = new DatabaseManager();
    private Map<String, JLabel> countLabels = new LinkedHashMap<>();
    private List<PickupRequest> shown = new ArrayList<>();      // requests currently in the table
    private JTable table = Theme.table("ID", "User", "Item", "Category", "Qty", "Pickup Date", "Status");
    private JComboBox<String> filterBox = new JComboBox<>();
    private JComboBox<Status> statusBox = new JComboBox<>(Status.values());

    AdminDashboard(Person admin) {
        super("E-Waste Management - Admin Dashboard");
        setSize(1100, 680);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        add(Theme.header("Admin Dashboard", admin.getFullName() + "  (" + admin.getRole() + ")", e -> {
            dispose();
            new LoginScreen().setVisible(true);
        }), BorderLayout.NORTH);
        // summary cards: total + one per status
        JPanel cards = new JPanel(new GridLayout(1, 6, 14, 0));
        cards.setOpaque(false);
        for (String n : new String[]{"TOTAL", "PENDING", "APPROVED", "COLLECTED", "RECYCLED", "REJECTED"}) {
            JLabel value = new JLabel("0");
            countLabels.put(n, value);
            boolean total = n.equals("TOTAL");
            cards.add(Theme.card(total ? "Total Requests" : n.charAt(0) + n.substring(1).toLowerCase(),
                    value, total ? Theme.TEAL : Theme.statusColor(n)));
        }
        // toolbar: filter on the left, status change + recycling on the right
        filterBox.addItem("ALL");
        for (Status s : Status.values()) filterBox.addItem(s.name());
        filterBox.addActionListener(e -> loadData());
        JButton updateBtn = Theme.button("Update Status", Theme.GREEN);
        JButton recycleBtn = Theme.button("Record Recycling", Theme.TEAL);
        updateBtn.addActionListener(e -> updateSelectedStatus());
        recycleBtn.addActionListener(e -> recycleSelected());
        JPanel tools = new JPanel(new BorderLayout());
        tools.setOpaque(false);
        tools.add(Theme.row(FlowLayout.LEFT, 10, new JLabel("Filter by status:"), filterBox), BorderLayout.WEST);
        tools.add(Theme.row(FlowLayout.RIGHT, 10, new JLabel("Change selected to:"), statusBox, updateBtn, recycleBtn), BorderLayout.EAST);
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(4).setMaxWidth(60);
        add(Theme.page(cards, tools, table), BorderLayout.CENTER);
        loadData();
    }

    void loadData() {
        try {
            String filter = (String) filterBox.getSelectedItem();
            DefaultTableModel model = (DefaultTableModel) table.getModel();
            model.setRowCount(0);
            shown.clear();
            for (PickupRequest r : db.getAllRequests()) {
                if (filter == null || filter.equals("ALL") || r.getStatus().name().equals(filter)) {
                    shown.add(r);
                    model.addRow(new Object[]{r.getId(), r.getUsername(), r.getItemName(), r.getCategory(),
                            r.getQuantity(), r.getPickupDate(), r.getStatus().name()});
                }
            }
            int total = 0;
            for (Map.Entry<String, Integer> e : db.countByStatus(null).entrySet()) {
                countLabels.get(e.getKey()).setText("" + e.getValue());
                total += e.getValue();
            }
            countLabels.get("TOTAL").setText("" + total);
        } catch (SQLException ex) {
            Theme.showError(this, ex);
        }
    }

    private PickupRequest selectedRequest() {
        int row = table.getSelectedRow();
        if (row < 0) {
            Theme.showMessage(this, "Please select a request in the table first.", true);
            return null;
        }
        return shown.get(row);
    }

    private void updateSelectedStatus() {
        PickupRequest r = selectedRequest();
        if (r == null) return;
        Status target = (Status) statusBox.getSelectedItem();
        try {
            db.updateStatus(r.getId(), target);
            Theme.showMessage(this, "Request #" + r.getId() + " is now " + target + ".");
            loadData();
        } catch (InvalidInputException | SQLException ex) {
            Theme.showError(this, ex);   // e.g. RECYCLED -> APPROVED is blocked by Status.canMoveTo
        }
    }

    private void recycleSelected() {
        PickupRequest r = selectedRequest();
        if (r == null) return;
        if (r.getStatus() != Status.COLLECTED) {
            Theme.showMessage(this, "Only COLLECTED requests can be recycled (this one is " + r.getStatus() + ").", true);
            return;
        }
        buildRecycleDialog(r).setVisible(true);
        loadData();
    }

    /** Builds (does not show) the Record Recycling form for a COLLECTED request. */
    JDialog buildRecycleDialog(PickupRequest r) {
        JLabel info = new JLabel("<html><b>Request #" + r.getId() + "</b> - " + r.getItemName() + " (" + r.getCategory()
                + ")<br>User: " + r.getUsername() + " | Requested quantity: " + r.getQuantity() + "</html>");
        JTextField qty = Theme.field(new JTextField("" + r.getQuantity(), 24));
        JTextField method = Theme.field(new JTextField(PickupRequest.suggestMethod(r.getCategory()), 24));
        JTextField remarks = Theme.field(new JTextField("Recycled at certified facility", 24));
        JPanel form = new JPanel(new BorderLayout(0, 12));
        form.add(info, BorderLayout.NORTH);
        form.add(Theme.form("Recycled quantity", qty, "Recycling method", method, "Remarks", remarks), BorderLayout.CENTER);
        JButton save = Theme.button("Save and Mark RECYCLED", Theme.GREEN);
        save.addActionListener(e -> {
            Window d = SwingUtilities.getWindowAncestor(save);
            try {
                db.addRecyclingRecord(r.getId(), Integer.parseInt(qty.getText().trim()), method.getText().trim(), remarks.getText().trim());
                Theme.showMessage(d, "Recycling recorded. Request #" + r.getId() + " is now RECYCLED.");
                d.dispose();
            } catch (InvalidInputException | SQLException | NumberFormatException ex) {
                Theme.showError(d, ex);
            }
        });
        return Theme.dialog(this, "Record Recycling", form, save);
    }
}

public class EWasteApp {
    /** Look and feel: FlatLaf (jar in lib\) loaded by name, so javac works even without the jar. */
    static void setupLook() {
        Color headerGreen = new Color(0x14614A);
        UIManager.put("Button.arc", 12);
        UIManager.put("Button.borderWidth", 0);
        UIManager.put("Component.focusWidth", 0);
        UIManager.put("Component.arc", 10);
        UIManager.put("Component.focusColor", Theme.GREEN);
        UIManager.put("Table.alternateRowColor", new Color(0xF6FAF8));
        UIManager.put("Table.selectionBackground", new Color(0xCFEBDD));
        UIManager.put("Table.selectionForeground", new Color(0x1E2B25));
        UIManager.put("Table.selectionInactiveBackground", new Color(0xCFEBDD));
        UIManager.put("Table.selectionInactiveForeground", new Color(0x1E2B25));
        UIManager.put("Table.cellMargins", new Insets(1, 10, 1, 10));
        UIManager.put("TableHeader.background", headerGreen);
        UIManager.put("TableHeader.foreground", Color.WHITE);
        UIManager.put("TableHeader.font", Theme.BOLD);
        UIManager.put("TableHeader.cellMargins", new Insets(4, 10, 4, 10));
        UIManager.put("TableHeader.separatorColor", headerGreen);
        UIManager.put("TableHeader.bottomSeparatorColor", headerGreen);
        UIManager.put("defaultFont", new Font("Segoe UI", Font.PLAIN, 14));
        try {
            UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf");
        } catch (Exception e) {
            // jar missing: the default Swing look is used
        }
    }

    public static void main(String[] args) {
        setupLook();
        SwingUtilities.invokeLater(() -> new LoginScreen().setVisible(true));
    }
}
