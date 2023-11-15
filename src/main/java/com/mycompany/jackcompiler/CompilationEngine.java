package com.mycompany.jackcompiler;
import java.io.*;


//subroutine call pop temp? why line 89 call Keyboard.Pressed
//arrays

public class CompilationEngine {
    private JackTokenizer tokenizer;
    private VMWriter writer;
    private SymbolTable symTable;
    private String className;
    private String currSubName;
    private boolean subScope;
    private int ifLabelIndex = 0;
    private int whileLabelIndex = 0;
    
    
    public CompilationEngine(BufferedReader iFile, BufferedWriter ofile){
        tokenizer = new JackTokenizer(iFile);
        writer = new VMWriter(ofile);
        symTable = new SymbolTable();
        subScope = false;
    }
    
    public void CompileClass(){
        tokenizer.advance(); //keyword class
        tokenizer.advance(); // identifier class name
        className = tokenizer.identifier();
        tokenizer.advance(); //{
        tokenizer.advance();
        while(tokenizer.keyWord().equals("static") || tokenizer.keyWord().equals("field")){
            CompileClassVarDec();
        }

        while(tokenizer.keyWord().equals("constructor") || tokenizer.keyWord().equals("function") || tokenizer.keyWord().equals("method")){
            CompileSubroutine();
        }
        tokenizer.advance();
    }
    
    public void CompileClassVarDec(){
        String kind, type, name;
            kind = tokenizer.keyWord();//Static or field
            tokenizer.advance();
            type = tokenizer.keyWord();//type
            tokenizer.advance();
            name = tokenizer.identifier();//identifier
            symTable.Define(name, type, kind);
            tokenizer.advance();
            while(tokenizer.symbol() == ','){
                tokenizer.advance();  //,
                name = tokenizer.identifier(); 
                tokenizer.advance();
                symTable.Define(name, type, kind);
            }

            tokenizer.advance();//;
    }
    
    
    public void CompileSubroutine() {
        ifLabelIndex = 0; whileLabelIndex = 0;
        symTable.startSubroutine();
        String funcType = tokenizer.keyWord();
        if(funcType.equals("method")) {symTable.Define("this", className, "ARG");}
        tokenizer.advance(); //method or function or constructor
        tokenizer.advance(); // type
        currSubName = tokenizer.identifier();
        tokenizer.advance(); // identifier
        tokenizer.advance(); // (
        CompileParameterList();
        tokenizer.advance(); // )
        tokenizer.advance(); // {
        while (tokenizer.keyWord().equals("var") ) {
            CompileVarDec();
        }
        writer.WriteFunction(className + "." + currSubName, symTable.VarCount("var"));
        if(funcType.equals("constructor")){
            int classVarCnt = symTable.VarCount("static")+symTable.VarCount("field");
            writer.writePush("constant", classVarCnt);
            writer.WriteCall("Memory.alloc", 1);
            writer.writePop("pointer", 0);
//            for(int i = 0; i < classVarCnt;i++){
//                writer.writePush("argument", i);
//                writer.writePop("this", i);
//            }
        }
        if(funcType.equals("method")){ //Pointer to class
            writer.writePush("argument", 0); //Push this onto stack
            writer.writePop("pointer", 0); 
        }
//        for(int i = 0; i < symTable.VarCount("ARG");i++){
//                writer.writePush("argument", i);
//                writer.writePop("this", i);
//        }
//        writer.writePush("pointer", 0);
        while(tokenizer.keyWord().equals("let") || tokenizer.keyWord().equals("if") || tokenizer.keyWord().equals("while") || tokenizer.keyWord().equals("do")){
            CompileStatements();
        }
        CompileReturn();
        tokenizer.advance();//}

    }
    
    public void CompileParameterList() {
        String name, type;
        if (tokenizer.symbol() != ')') {
            while (tokenizer.keyWord().equals("int") || tokenizer.keyWord().equals("char") || tokenizer.keyWord().equals("boolean") || tokenizer.tokenType().equals("indentifier")) {
                type = tokenizer.keyWord();
                tokenizer.advance(); // type
                name = tokenizer.identifier();
                tokenizer.advance(); // identifier
                symTable.Define(name, type, "argument");
                if (tokenizer.symbol() == ',') {
                    tokenizer.advance(); // ,
                }
            }
        }
    }
    
    public void CompileVarDec(){
        String type, name;
        tokenizer.advance(); //var
        type = tokenizer.keyWord();
        tokenizer.advance(); //type
        name = tokenizer.identifier();
        tokenizer.advance(); //identifier
        symTable.Define(name, type, "var");
        while(tokenizer.symbol() == ','){
            tokenizer.advance(); //,
            name = tokenizer.identifier();
            tokenizer.advance(); //identifier
            symTable.Define(name, type, "var");
        }
        tokenizer.advance(); //;
    }
    
    public void CompileStatements(){
        if(tokenizer.symbol() != '}'){
            while(tokenizer.keyWord().equals("let") || tokenizer.keyWord().equals("if") || tokenizer.keyWord().equals("while")|| tokenizer.keyWord().equals("do")){
                switch(tokenizer.keyWord()){
                    case "let" -> {CompileLet();}
                    case "do" -> {CompileDo();}
                    case "if" -> CompileIf();
                    case "while" -> CompileWhile();
                    default -> {}
                }
            }
        }
    }
    
    public void CompileDo(){
        tokenizer.advance(); //do
        String name = tokenizer.identifier();
        tokenizer.advance(); //Subroutine name or className or varName
        CompileSubroutineCall(name);
        tokenizer.advance(); //;
    }
    
    public void CompileLet(){
        tokenizer.advance();//advance past let
        String identifier = tokenizer.identifier();
        String segment;
        switch(symTable.KindOf(identifier)){
            case "var" -> segment = "local";
            case "field" ->segment = "this";
            default -> segment = symTable.KindOf(identifier) ;
        }
        tokenizer.advance();// advance past identifier
        if(tokenizer.symbol()== '['){
            tokenizer.advance(); //Advance Past [
            CompileExpression(); 
            tokenizer.advance(); //Advance Past ]
        }
        tokenizer.advance();//advance past =
        CompileExpression();
        tokenizer.advance();//advnace past ;
//        tokenizer.advance(); //;
        
        writer.writePop(segment, symTable.IndexOf(identifier));
    }
    
    public void CompileWhile(){
        int ind = whileLabelIndex;
        whileLabelIndex++;
        writer.WriteLabel("WHILE_EXP"+Integer.toString(ind));
        tokenizer.advance();//while
        tokenizer.advance(); // (
        CompileExpression();
        tokenizer.advance(); // )
        writer.writeArithmetic("not");
        writer.WriteIf("WHILE_END"+Integer.toString(ind));
        tokenizer.advance(); // {
        CompileStatements();
        writer.WriteGoto("WHILE_EXP"+Integer.toString(ind));
        tokenizer.advance(); // }
        writer.WriteLabel("WHILE_END"+Integer.toString(ind));
        whileLabelIndex++;
    }
    
    public void CompileReturn(){
        tokenizer.advance(); // return
        if(tokenizer.symbol() != ';'){
            CompileExpression();
        }
        else{
            writer.writePush("constant", 0);
        }
        tokenizer.advance(); //;
        writer.writeReturn();
    }
    
    public void CompileIf(){
        tokenizer.advance(); //if
        tokenizer.advance();//(
        CompileExpression(); //
        writer.WriteIf("IF_TRUE"+Integer.toString(ifLabelIndex));
        writer.WriteGoto("IF_FALSE"+Integer.toString(ifLabelIndex));
        tokenizer.advance();//)
        tokenizer.advance();//{
        writer.WriteLabel("IF_TRUE"+Integer.toString(ifLabelIndex));
        CompileStatements();
        tokenizer.advance();//}
        if(tokenizer.keyWord().equals("else")){
            tokenizer.advance();//else
            tokenizer.advance();//{
            CompileStatements();
            tokenizer.advance();//}
        }
        writer.WriteLabel("IF_FALSE"+Integer.toString(ifLabelIndex));
        ifLabelIndex++;
    }
    
    public void CompileExpression(){
        char operator;
        CompileTerm();
        while(isOp()){
            operator = tokenizer.symbol();
            tokenizer.advance();
            CompileTerm();
            switch(operator){
                case '+'-> writer.writeArithmetic("add"); 
                case '-'-> writer.writeArithmetic("sub");
                case '*'-> writer.writeArithmetic("mult");
                case '/'-> writer.writeArithmetic("div"); 
                case '&'-> writer.writeArithmetic("and");
                case '|'-> writer.writeArithmetic("or"); 
                case '<'-> writer.writeArithmetic("lt"); 
                case '>'-> writer.writeArithmetic("gt");
                case '='-> writer.writeArithmetic("eq");
                default -> {}
            }

        }
    }
    
    public void CompileTerm(){
        switch (tokenizer.symbol()) {
            case '(' -> {
                tokenizer.advance(); // (
                CompileExpression();
                tokenizer.advance(); // )
            }
            case '~' -> {
                tokenizer.advance(); // ~ 
                CompileTerm();
                writer.writeArithmetic("not");

            }
            case '-' ->{
                tokenizer.advance(); // -
                CompileTerm();
            }
            default -> {
                switch(tokenizer.tokenType()){
                    case "INT_CONST" -> {
                        writer.writePush("constant", tokenizer.intVal());
                        tokenizer.advance();
                    }
                    case "KEYWORD"-> {
                        switch (tokenizer.keyWord()) {
                            case "this" -> writer.writePush("pointer", 0);
                            case "true" -> {
                                writer.writePush("constant", 0);
                                writer.writeArithmetic("not");
                            }
                            case "false" -> writer.writePush("constant", 0);
                            default -> {
                            }
                        }
                        tokenizer.advance();
                        }
                    case "IDENTIFIER" -> { 
                        String name = tokenizer.identifier();
                        tokenizer.advance(); 
                        switch (tokenizer.symbol()) {
                            case '(', '.' -> CompileSubroutineCall(name);
                            case '[' -> {
                                String segment;
                                String kind = symTable.KindOf(name);
                                if (kind.equals("var"))
                                    segment = "local";
                                else if (kind.equals("field"))
                                    segment = "this";
                                else
                                    segment = symTable.KindOf(name);
                                writer.writePush(segment, symTable.IndexOf(name));
                                tokenizer.advance();// [
                                CompileExpression();
                                tokenizer.advance(); // ]
                                writer.writeArithmetic("add");
                                writer.writePop("pointer", 1);
                            }
                            default -> {
                                String segment;
                                String kind = symTable.KindOf(name);
                                if (kind.equals("var"))
                                    segment = "local";
                                else if (kind.equals("field"))
                                    segment = "this";
                                else
                                    segment = symTable.KindOf(name);
                                writer.writePush(segment,symTable.IndexOf(name));       
                            }
                        }
                        }
                    default ->{}
                }
            }
        }
    }
    
    public void CompileSubroutineCall(String name){
        int nArgs = 0;
        String subIdentifier;
        boolean isMethod = false; 
        boolean isConstructor = false;
        boolean isClassInstance = false;
        if(tokenizer.symbol() == '.'){
            tokenizer.advance(); //.
            if(tokenizer.identifier().equals("new")){isConstructor = true;}
            if(symTable.TypeOf(name) != null) {
                String segment;
                String kind = symTable.KindOf(name);
                if (kind.equals("var"))
                    segment = "local";
                else if (kind.equals("field"))
                    segment = "this";
                else
                    segment = symTable.KindOf(name);
                writer.writePush(segment, symTable.IndexOf(name));
                isClassInstance = true;
                nArgs++;
                name = symTable.TypeOf(name);
                }
            subIdentifier = name + "." + tokenizer.identifier();
            tokenizer.advance(); //subRoutineName
        }else{
            subIdentifier = className + "." + name;
            isMethod = true;
        }
        if(isMethod){
            //though this should be this instead of pointer may need change
            //also another saying it should be local
            //pointer? local? this?
            writer.writePush("pointer", 0);
            nArgs++;
        }
        tokenizer.advance(); //(
        if(tokenizer.symbol() != ')'){
            CompileExpression();
            nArgs++;
            while(tokenizer.symbol() == ','){
                tokenizer.advance(); //,
                CompileExpression();
                nArgs++;
            }
        }
        tokenizer.advance();// )
        writer.WriteCall(subIdentifier, nArgs);
        if(!isConstructor) 
            writer.writePop("temp", 0);
    }
    

    public void closeFiles(){
        try{
            tokenizer.close();
            writer.close();
        }catch(IOException e){
        
        }
    }
    
    public boolean isOp(){
        return switch(tokenizer.symbol()){
            case '+', '-', '*', '/', '&', '|', '<', '>', '=' -> true;
            default -> false;
        };
    }
    
}
