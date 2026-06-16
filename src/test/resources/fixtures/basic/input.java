package com.example;
public class Basic{
    private  final String   name;
    public Basic(String name){this.name=name;}
    public String greet( ){
        int count=1+2*3;
        return "Hi "+name+" "+count;
    }
}
