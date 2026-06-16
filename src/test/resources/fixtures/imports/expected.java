package com.example;

import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import com.example.other.Thing;

import static org.testng.Assert.assertEquals;

public class Imports {
  List<Thing> things = new ArrayList<>();
  DataSource ds;
}
