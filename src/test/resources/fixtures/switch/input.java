package com.example;
public class Switch {
  String describe(int code){
    return switch(code){
      case 1->"one";
      case 2,3->"few";
      default->"many";
    };
  }
  void old(int x){
    switch(x){
      case 1:
        doA();
        break;
      default:
        doB();
    }
  }
}
