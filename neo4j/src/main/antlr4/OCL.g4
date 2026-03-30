grammar OCL;

@header {
    package org.uet.dse.neo4j;
}

oclFile : (declaration | expression)* EOF ;

declaration : 'context' className=Identifier 'inv' invName=Identifier? ':' expression ;

expression
    : primary                                                       #PrimaryExp
    | left=expression op=('*' | '/') right=expression                #MultiplicativeExp
    | left=expression op=('+' | '-') right=expression                #AdditiveExp
    | left=expression op=('=' | '<>' | '<' | '>' | '>=' | '<=') right=expression #ComparisonExp
    | op='not' expression                                           #NotExp
    | left=expression op=('and' | 'or') right=expression            #LogicalExp
    | left=expression op='implies' right=expression                 #LogicalImpliesExp
    ;

primary
    : literal                                      #LiteralExpr
    | Identifier                                   #IdExpr
    | primary '.' Identifier '(' argList? ')'     #MethodCallExpr
    | primary '.' Identifier                       #NavigationExpr
    | primary '->' Identifier '(' Identifier '|' expression ')' #IteratorExpr
    | primary '->' Identifier '(' argList? ')'     #CollectionOpExpr
    | '(' expression ')'                           #ParenExpr
    ;

argList : expression (',' expression)* ;

literal : Number | StringLiteral | 'true' | 'false' | 'self' | 'null' ;

Identifier : [a-zA-Z_][a-zA-Z0-9_]* ;
Number : [0-9]+ ('.' [0-9]+)? ;
StringLiteral : '\'' (~['])* '\'' ;
WS : [ \t\r\n]+ -> skip ;