grammar OCL;

oclFile : (declaration | expression)* EOF ;

declaration
    : 'context' className=Identifier 'inv' invName=Identifier? ':' expression
    | 'context' className=Identifier '::' operationName=Identifier '(' paramList? ')' operationConstraintKind ruleName=Identifier? ':' expression
    | 'context' className=Identifier '::' attributeName=Identifier attributeConstraintKind ruleName=Identifier? ':' expression
    ;

operationConstraintKind
    : 'pre'
    | 'post'
    | 'body'
    ;

attributeConstraintKind
    : 'init'
    | 'derive'
    ;

paramList
    : paramDecl (',' paramDecl)*
    ;

paramDecl
    : Identifier (':' Identifier)?
    ;

expression
    : primary                                                       #PrimaryExp
    | left=expression op=('*' | '/') right=expression                #MultiplicativeExp
    | left=expression op=('+' | '-') right=expression                #AdditiveExp
    | left=expression op=('=' | '<>' | '<' | '>' | '>=' | '<=') right=expression #ComparisonExp
    | op='not' expression                                           #NotExp
    | left=expression op=('and' | 'or' | 'xor') right=expression    #LogicalExp
    | left=expression op='implies' right=expression                 #LogicalImpliesExp
    ;

primary
    : literal                                      #LiteralExpr
    | enumType=Identifier '::' enumLiteral=Identifier #EnumLiteralExpr
    | Identifier                                   #IdExpr
    | 'if' condition=expression 'then' thenBranch=expression 'else' elseBranch=expression 'endif' #IfExp
    | primary '.' Identifier '(' argList? ')'     #MethodCallExpr
    | primary '.' Identifier ('[' argList? ']')?  #NavigationExpr
    | primary '->' iteratorOp=Identifier '(' iteratorVar=Identifier (':' iteratorType=Identifier)? '|' expression ')' #IteratorExpr
    | primary '->' Identifier '(' argList? ')'     #CollectionOpExpr
    | 'let' varName=Identifier '=' value=expression 'in' body=expression #LetExpr
    | 'Set' '{' argList? '}'                         #SetLiteralExpr
    | '(' expression ')'                           #ParenExpr
    ;

argList : expression (',' expression)* ;

literal : Number | StringLiteral | 'true' | 'false' | 'self' | 'null' ;

Identifier : [a-zA-Z_][a-zA-Z0-9_]* ;
Number : [0-9]+ ('.' [0-9]+)? ;
StringLiteral : '\'' (~['])* '\'' ;
WS : [ \t\r\n]+ -> skip ;
