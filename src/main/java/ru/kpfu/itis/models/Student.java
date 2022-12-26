package ru.kpfu.itis.models;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Student {
    private String firstName;
    private String secondName;
    private int age;
    private String[] courses;
}
