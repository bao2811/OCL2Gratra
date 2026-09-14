grammar OCL;

/*
 * OCL 2.4-aligned concrete syntax for the Neo4j front end.
 *
 * This grammar deliberately parses a wider surface language than the proved
 * OCL_val fragment. OclValAdmissionPolicy is the closed-world boundary that
 * decides which successfully parsed trees may enter the certified pipeline.
 */

oclFile
    : declaration* expression? EOF
    ;

// Explicit entry points used by UI, certified compilation and grammar tests.
oclDocument
    : declaration* EOF
    ;

constraintDocument
    : declaration+ EOF
    ;

invariantOnly
    : CONTEXT contextType=qualifiedName INV ruleName=Identifier? COLON expression EOF
    ;

expressionOnly
    : expression EOF
    ;

typeOnly
    : typeRef EOF
    ;

declaration
    : CONTEXT contextType=qualifiedName INV ruleName=Identifier? COLON expression
    | CONTEXT contextType=qualifiedName DCOLON operationName=Identifier
      LPAREN paramList? RPAREN (COLON returnType=typeRef)?
      operationConstraintKind ruleName=Identifier? COLON expression
    | CONTEXT contextType=qualifiedName DCOLON attributeName=Identifier
      (COLON attributeType=typeRef)? attributeConstraintKind ruleName=Identifier? COLON expression
    ;

operationConstraintKind
    : PRE
    | POST
    | BODY
    ;

attributeConstraintKind
    : INIT
    | DERIVE
    ;

paramList
    : paramDecl (COMMA paramDecl)*
    ;

paramDecl
    : name=Identifier (COLON type=typeRef)?
    ;

typeRef
    : qualifiedName
    | collectionType
    ;

collectionType
    : collectionKind LPAREN typeRef RPAREN
    ;

qualifiedName
    : Identifier (DCOLON Identifier)*
    ;

expression
    : letExpression
    ;

letExpression
    : LET letBinding (COMMA letBinding)* IN expression
    | impliesExpression
    ;

letBinding
    : name=Identifier (COLON type=typeRef)? EQUAL expression
    ;

// OCL implies is right associative and has the lowest logical precedence.
impliesExpression
    : left=orExpression (IMPLIES right=impliesExpression)?
    ;

orExpression
    : first=xorExpression (operators+=OR rest+=xorExpression)*
    ;

xorExpression
    : first=andExpression (operators+=XOR rest+=andExpression)*
    ;

andExpression
    : first=equalityExpression (operators+=AND rest+=equalityExpression)*
    ;

equalityExpression
    : first=relationalExpression (operators+=(EQUAL | NOT_EQUAL) rest+=relationalExpression)*
    ;

relationalExpression
    : first=additiveExpression
      (operators+=(LESS | GREATER | LESS_EQUAL | GREATER_EQUAL) rest+=additiveExpression)*
    ;

additiveExpression
    : first=multiplicativeExpression (operators+=(PLUS | MINUS) rest+=multiplicativeExpression)*
    ;

multiplicativeExpression
    : first=unaryExpression (operators+=(STAR | SLASH | DIV | MOD) rest+=unaryExpression)*
    ;

unaryExpression
    : op=(NOT | PLUS | MINUS) unaryExpression
    | postfixExpression
    ;

postfixExpression
    : primaryExpression postfixPart*
    ;

postfixPart
    : DOT featureName=Identifier qualifierList* atPre? argumentList?             #DotPostfix
    | ARROW operationName=Identifier LPAREN iteratorDeclarationList SEMICOLON
      accumulator=letBinding BAR expression RPAREN                               #IteratePostfix
    | ARROW operationName=Identifier LPAREN iteratorDeclarationList BAR expression RPAREN #IteratorPostfix
    | ARROW operationName=Identifier argumentList                               #ArrowOperationPostfix
    ;

iteratorDeclarationList
    : variableDeclaration (COMMA variableDeclaration)*
    ;

variableDeclaration
    : name=Identifier (COLON type=typeRef)?
    ;

qualifierList
    : LBRACK argumentValues? RBRACK
    ;

atPre
    : AT PRE
    ;

argumentList
    : LPAREN argumentValues? RPAREN
    ;

argumentValues
    : expression (COMMA expression)*
    ;

primaryExpression
    : IF condition=expression THEN thenBranch=expression ELSE elseBranch=expression ENDIF
    | literal
    | qualifiedName
    | collectionLiteral
    | LPAREN expression RPAREN
    ;

collectionLiteral
    : collectionKind LBRACE (collectionLiteralPart (COMMA collectionLiteralPart)*)? RBRACE
    ;

collectionKind
    : SET
    | BAG
    | SEQUENCE
    | ORDERED_SET
    | COLLECTION
    ;

collectionLiteralPart
    : first=expression (RANGE last=expression)?
    ;

literal
    : IntegerLiteral
    | RealLiteral
    | StringLiteral
    | TRUE
    | FALSE
    | NULL
    | INVALID
    ;

CONTEXT     : 'context';
INV         : 'inv';
PRE         : 'pre';
POST        : 'post';
BODY        : 'body';
INIT        : 'init';
DERIVE      : 'derive';
LET         : 'let';
IN          : 'in';
IF          : 'if';
THEN        : 'then';
ELSE        : 'else';
ENDIF       : 'endif';
IMPLIES     : 'implies';
OR          : 'or';
XOR         : 'xor';
AND         : 'and';
NOT         : 'not';
DIV         : 'div';
MOD         : 'mod';
TRUE        : 'true';
FALSE       : 'false';
NULL        : 'null';
INVALID     : 'invalid';
SET         : 'Set';
BAG         : 'Bag';
SEQUENCE    : 'Sequence';
ORDERED_SET : 'OrderedSet';
COLLECTION  : 'Collection';

DCOLON        : '::';
ARROW         : '->';
RANGE         : '..';
NOT_EQUAL     : '<>';
LESS_EQUAL    : '<=';
GREATER_EQUAL : '>=';
EQUAL         : '=';
LESS          : '<';
GREATER       : '>';
PLUS          : '+';
MINUS         : '-';
STAR          : '*';
SLASH         : '/';
DOT           : '.';
COMMA         : ',';
SEMICOLON     : ';';
COLON         : ':';
BAR           : '|';
AT            : '@';
LPAREN        : '(';
RPAREN        : ')';
LBRACE        : '{';
RBRACE        : '}';
LBRACK        : '[';
RBRACK        : ']';

RealLiteral
    : [0-9]+ '.' [0-9]+
    ;

IntegerLiteral
    : [0-9]+
    ;

StringLiteral
    : '\'' ('\'\'' | ~['\r\n])* '\''
    ;

Identifier
    : [a-zA-Z_] [a-zA-Z0-9_]*
    ;

LINE_COMMENT
    : '--' ~[\r\n]* -> skip
    ;

BLOCK_COMMENT
    : '/*' .*? '*/' -> skip
    ;

WS
    : [ \t\r\n]+ -> skip
    ;
