

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StoreServer extends Thread {
    String pattern = "register:(?<id>\\d+):(?<name>\\w+):(?<money>\\d+)";
    String patternLogin = "login:(?<id>\\d+)";
    String patternGetPrice = "get price:(?<name>\\w+)";
    String patternGetQuantity = "get quantity:(?<name>\\w+)";
    String patternCharge = "charge:(?<money>\\d+)";
    String patternPurchase = "purchase:(?<name>\\w+):(?<quantity>\\d+)";
    private DataOutputStream dataOutputStream;
    private DataInputStream dataInputStream;
    private static ServerSocket server;
    private Socket socket;
    private static final Object inventoryLock = new Object();
    private static final Object customersLock = new Object();

    public StoreServer(Socket socket1) {
        this.socket = socket1;
    }

    private static Map<String, Integer> inventory = new HashMap<>();
    private static Map<String, Customer> customers = new HashMap<>();

    private Customer currentCustomer;

    @Override
    public void run() {
        try {
            String clientRequest;
            DataInputStream dataInputStream = new DataInputStream((socket.getInputStream()));
            DataOutputStream dataOutputStream = new DataOutputStream((socket.getOutputStream()));
            while (true) {
                clientRequest = dataInputStream.readUTF();
                Pattern.compile(pattern);
                Matcher matcherRegister = Pattern.compile(pattern).matcher(clientRequest);
                Matcher matcherLogin = Pattern.compile(patternLogin).matcher(clientRequest);
                Matcher matcherGetPrice = Pattern.compile(patternGetPrice).matcher(clientRequest);
                Matcher matcherGetQuantity = Pattern.compile(patternGetQuantity).matcher(clientRequest);
                Matcher matcherCharge = Pattern.compile(patternCharge).matcher(clientRequest);
                Matcher matcherPurchase = Pattern.compile(patternPurchase).matcher(clientRequest);

                if (matcherRegister.matches()) {
                    String id = matcherRegister.group("id");
                    String name = matcherRegister.group("name");
                    String moneyStr = matcherRegister.group("money");

                    if (isValidId(id) && isValidName(name) && isValidMoney(moneyStr)) {
                        if (userExists(id, name)) {
                            dataOutputStream.writeUTF("User with this ID or name already exists!");
                        } else {
                            Customer customer = new Customer(name, id, Integer.parseInt(moneyStr));
                            synchronized (customersLock) {
                                customers.put(id, customer);
                            }
                            dataOutputStream.writeUTF("register successfully");
                        }
                    } else {
                        if (!isValidId(id)) {
                            dataOutputStream.writeUTF("invalid id!");
                        } else if (!isValidName(name)) {
                            dataOutputStream.writeUTF("invalid name!");
                        } else {
                            dataOutputStream.writeUTF("invalid money!");
                        }
                    }
                }else if (matcherLogin.matches()) {
                    if (isValidId(matcherLogin.group("id"))) {
                        currentCustomer = getCustomerById(Integer.parseInt(matcherLogin.group("id")));
                        dataOutputStream.writeUTF("logged in successfully!");
                    } else {
                        dataOutputStream.writeUTF("invalid id!");
                    }
                } else if (clientRequest.equals("logout")) {
                    currentCustomer = null;
                    dataOutputStream.writeUTF("logout successfully!");
                } else if (matcherGetPrice.matches()) {
                    if (getPrice(matcherGetPrice.group("name")) != -1) {
                        dataOutputStream.writeUTF("price: " + getPrice(matcherGetPrice.group("name")));
                    } else {
                        dataOutputStream.writeUTF("invalid shoe name");
                    }
                } else if (clientRequest.equals("get money")) {
                    getCustomerMoney(dataOutputStream);
                } else if (matcherGetQuantity.matches()) {
                    if (getQuantity(matcherGetQuantity.group("name")) != -1) {
                        dataOutputStream.writeUTF("quantity: " + getQuantity(matcherGetQuantity.group("name")));
                    } else {
                        dataOutputStream.writeUTF("invalid shoe name");
                    }

                } else if (matcherCharge.matches()) {
                    if (currentCustomer != null) {
                        if (isValidMoney(matcherCharge.group("money"))) {
                            chargeCustomer(Integer.parseInt(matcherCharge.group("money")), dataOutputStream);
                        } else {
                            dataOutputStream.writeUTF("invalid money!");
                        }
                    } else {
                        dataOutputStream.writeUTF("no customers have logged in!");
                    }
                } else if (matcherPurchase.matches()) {
                    if (currentCustomer != null) {
                        if (isValidProductName(matcherPurchase.group("name"))) {
                            if (isValidQuantity(matcherPurchase.group("quantity"))) {
                                purchaseProduct(matcherPurchase.group("name"), Integer.parseInt(matcherPurchase.group("quantity")), dataOutputStream);
                            } else {
                                dataOutputStream.writeUTF("invalid quantity!");
                            }
                        } else {
                            dataOutputStream.writeUTF("invalid shoe name");
                        }
                    } else {
                        dataOutputStream.writeUTF("no customers have logged in!");
                    }
                } else if (clientRequest.equals("exit")) {
                    dataOutputStream.writeUTF("exit successfully");
                    break;
                }else{
                    dataOutputStream.writeUTF("invalid command!");
                }
            }
            dataOutputStream.close();
            dataInputStream.close();
            socket.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private boolean userExists(String id, String name) {
        synchronized (customersLock) {
            return customers.containsKey(id) || customers.values().stream().anyMatch(c -> c.getName().equals(name));
        }
    }
    private boolean isValidId(String id) {
        return id != null && id.matches("\\d+");
    }

    private boolean isValidName(String name) {
        return name != null && name.matches("[a-zA-Z\\s]+");
    }

    private boolean isValidMoney(String moneyStr) {
        try {
            int money = Integer.parseInt(moneyStr);
            return money > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isValidProductName(String productName) {
        return inventory.containsKey(productName);
    }

    private boolean isValidQuantity(String quantityStr) {
        try {
            int quantity = Integer.parseInt(quantityStr);
            return quantity > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void chargeCustomer(int chargeAmount, DataOutputStream dataOutputStream) throws IOException {
        if (currentCustomer != null) {
            currentCustomer.setMoney(currentCustomer.getMoney() + chargeAmount);
            dataOutputStream.writeUTF("charge successful, customer money: " + currentCustomer.getMoney());
        } else {
            dataOutputStream.writeUTF("no customers have logged in!");
        }
        dataOutputStream.flush();
    }


    private int getPrice(String productName) {
        if (inventory.containsKey(productName)) {
            return 10;
        } else {
            return -1;
        }

    }

    private int getQuantity(String productName) {
        if (inventory.containsKey(productName)) {
            return inventory.get(productName);
        } else {
            return -1;
        }
    }

    private void purchaseProduct(String productName, int quantity, DataOutputStream dataOutputStream) throws IOException {
        synchronized (inventoryLock) {
            if (inventory.containsKey(productName) && inventory.get(productName) >= quantity) {
                synchronized (customersLock) {
                    if (currentCustomer != null && currentCustomer.getMoney() >= quantity * 10) {
                        inventory.put(productName, inventory.get(productName) - quantity);
                        currentCustomer.setMoney(currentCustomer.getMoney() - (quantity * 10));
                        dataOutputStream.writeUTF("purchased successfully!");
                    } else {
                        dataOutputStream.writeUTF("not enough money!");
                    }
                }
            } else {
                dataOutputStream.writeUTF("not enough products are there!");
            }
        }
        dataOutputStream.flush();
    }

    static Customer getCustomerById(int id) {
        synchronized (customersLock) {
            Integer number = id;
            return customers.getOrDefault(number.toString(), null);
        }
    }

    private void getCustomerMoney(DataOutputStream dataOutputStream) throws IOException {
        if (currentCustomer != null) {
            dataOutputStream.writeUTF("customer money: " + currentCustomer.getMoney());
        } else {
            dataOutputStream.writeUTF("no customers have logged in!");
        }
        dataOutputStream.flush();
    }

    public static void main(String[] args) {
        synchronized (inventoryLock) {
            inventory.put("shoe1", 5);
            inventory.put("shoe2", 5);
            inventory.put("shoe3", 5);
        }

        try {
            ServerSocket serverSocket = new ServerSocket(5000);
            while (true) {
                Socket socket = serverSocket.accept();
                Thread thread = new Thread(new StoreServer(socket));
                thread.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class Customer {
    private String name;
    private String id;
    private int money;

    public Customer(String name, String id, int money) {
        this.name = name;
        this.id = id;
        this.money = money;
    }

    public int getMoney() {
        return money;
    }

    public void setMoney(int money) {
        this.money = money;
    }

    public String getName() {
        return name;
    }
}
