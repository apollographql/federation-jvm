package com.apollographql.federation.user;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserService {

    private List<User> users = new ArrayList<>();
    private Map<User, Address> userAddressMap = new HashMap<>();

    @PostConstruct
    public void init() {
        User user1 = new User("1", "@ada", "Ada Lovelace");
        userAddressMap.put(user1, new Address(1l, "New York", "America"));
        users.add(user1);
        User user2 = new User("2", "@complete", "Alan Turing");
        userAddressMap.put(user2, new Address(2l, "Jamshedpur", "India"));
        users.add(user2);
    }

    @NotNull
    public User lookupUser(@NotNull String id) {
        User user1 = users.stream().filter(user -> user.getId().equals(id)).findAny().get();
        return user1;
    }

    public Address findAddressByUserId(String userId) {
        return userAddressMap.get(lookupUser(userId));
    }
}
