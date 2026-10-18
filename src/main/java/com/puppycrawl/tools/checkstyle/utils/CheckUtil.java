///////////////////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code and other text files for adherence to a set of rules.
// Copyright (C) 2001-2026 the original author or authors.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
///////////////////////////////////////////////////////////////////////////////////////////////

package com.puppycrawl.tools.checkstyle.utils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.FullIdent;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import com.puppycrawl.tools.checkstyle.checks.naming.AccessModifierOption;

/**
 * Contains utility methods for the checks.
 *
 */
public final class CheckUtil {

    /** Compiled pattern for all system newlines. */
    private static final Pattern ALL_NEW_LINES = Pattern.compile("\\R");

    /** Package separator. */
    private static final char PACKAGE_SEPARATOR = '.';

    /** Prevent instances. */
    private CheckUtil() {
    }

    /**
     * Tests whether a method definition AST defines an equals covariant.
     *
     * @param ast the method definition AST to test.
     *     Precondition: ast is a TokenTypes.METHOD_DEF node.
     * @return true if ast defines an equals covariant.
     */
    public static boolean isEqualsMethod(DetailAST ast) {
        boolean equalsMethod = false;

        if (ast.getType() == TokenTypes.METHOD_DEF) {
            final DetailAST modifiers = ast.findFirstToken(TokenTypes.MODIFIERS);
            final boolean staticOrAbstract =
                    modifiers.findFirstToken(TokenTypes.LITERAL_STATIC) != null
                    || modifiers.findFirstToken(TokenTypes.ABSTRACT) != null;

            if (!staticOrAbstract) {
                final DetailAST nameNode = ast.findFirstToken(TokenTypes.IDENT);
                final String name = nameNode.getText();

                if ("equals".equals(name)) {
                    // one parameter?
                    final DetailAST paramsNode = ast.findFirstToken(TokenTypes.PARAMETERS);
                    equalsMethod = paramsNode.getChildCount() == 1;
                }
            }
        }
        return equalsMethod;
    }

    /**
     * Returns the value represented by the specified string of the specified
     * type. Returns NaN for types other than float, double, int, and long.
     *
     * @param text the string to be parsed.
     * @param type the token type of the text. Should be a constant of
     *     {@link TokenTypes}.
     * @return the double value represented by the string argument.
     */
    public static double parseDouble(String text, int type) {
        return NumericLiteralUtil.parseDouble(text, type);
    }

    /**
     * Finds sub-node for given node minimal (line, column) pair.
     *
     * @param node the root of tree for search.
     * @return sub-node with minimal (line, column) pair.
     */
    public static DetailAST getFirstNode(final DetailAST node) {
        DetailAST currentNode = node;
        DetailAST child = node.getFirstChild();
        while (child != null) {
            final DetailAST newNode = getFirstNode(child);
            if (isBeforeInSource(newNode, currentNode)) {
                currentNode = newNode;
            }
            child = child.getNextSibling();
        }

        return currentNode;
    }

    /**
     * Retrieves whether ast1 is located before ast2.
     *
     * @param ast1 the first node.
     * @param ast2 the second node.
     * @return true, if ast1 is located before ast2.
     */
    public static boolean isBeforeInSource(DetailAST ast1, DetailAST ast2) {
        return ast1.getLineNo() < ast2.getLineNo()
            || TokenUtil.areOnSameLine(ast1, ast2)
                && ast1.getColumnNo() < ast2.getColumnNo();
    }

    /**
     * Retrieves the names of the type parameters to the node.
     *
     * @param node the parameterized AST node
     * @return a list of type parameter names
     */
    public static List<String> getTypeParameterNames(final DetailAST node) {
        final DetailAST typeParameters =
            node.findFirstToken(TokenTypes.TYPE_PARAMETERS);

        final List<String> typeParameterNames = new ArrayList<>();
        if (typeParameters != null) {
            final DetailAST typeParam =
                typeParameters.findFirstToken(TokenTypes.TYPE_PARAMETER);
            typeParameterNames.add(
                    typeParam.findFirstToken(TokenTypes.IDENT).getText());

            DetailAST sibling = typeParam.getNextSibling();
            while (sibling != null) {
                if (sibling.getType() == TokenTypes.TYPE_PARAMETER) {
                    typeParameterNames.add(
                            sibling.findFirstToken(TokenTypes.IDENT).getText());
                }
                sibling = sibling.getNextSibling();
            }
        }

        return typeParameterNames;
    }

    /**
     * Retrieves the type parameters to the node.
     *
     * @param node the parameterized AST node
     * @return a list of type parameter names
     */
    public static List<DetailAST> getTypeParameters(final DetailAST node) {
        final DetailAST typeParameters =
            node.findFirstToken(TokenTypes.TYPE_PARAMETERS);

        final List<DetailAST> typeParams = new ArrayList<>();
        if (typeParameters != null) {
            final DetailAST typeParam =
                typeParameters.findFirstToken(TokenTypes.TYPE_PARAMETER);
            typeParams.add(typeParam);

            DetailAST sibling = typeParam.getNextSibling();
            while (sibling != null) {
                if (sibling.getType() == TokenTypes.TYPE_PARAMETER) {
                    typeParams.add(sibling);
                }
                sibling = sibling.getNextSibling();
            }
        }

        return typeParams;
    }

    /**
     * Checks whether a method is a not void one.
     *
     * @param methodDefAst the method node.
     * @return true if method is a not void one.
     */
    public static boolean isNonVoidMethod(DetailAST methodDefAst) {
        boolean returnValue = false;
        if (methodDefAst.getType() == TokenTypes.METHOD_DEF) {
            final DetailAST typeAST = methodDefAst.findFirstToken(TokenTypes.TYPE);
            if (typeAST.findFirstToken(TokenTypes.LITERAL_VOID) == null) {
                returnValue = true;
            }
        }
        return returnValue;
    }

    /**
     * Checks whether a parameter is a receiver.
     *
     * <p>A receiver parameter is a special parameter that
     * represents the object for which the method is invoked.
     * It is denoted by the reserved keyword {@code this}
     * in the method declaration. Check
     * <a href="https://checkstyle.org/apidocs/com/puppycrawl/tools/checkstyle/api/TokenTypes.html#PARAMETER_DEF">
     * PARAMETER_DEF</a>
     * </p>
     *
     * @param parameterDefAst the parameter node.
     * @return true if the parameter is a receiver.
     * @see <a href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-8.html#jls-8.4.1">
     *     ReceiverParameter</a>
     */
    public static boolean isReceiverParameter(DetailAST parameterDefAst) {
        return parameterDefAst.findFirstToken(TokenTypes.IDENT) == null;
    }

    /**
     * Returns the access modifier of the method/constructor at the specified AST. If
     * the method is in an interface or annotation block, the access modifier is assumed
     * to be public.
     *
     * @param ast the token of the method/constructor.
     * @return the access modifier of the method/constructor.
     */
    public static AccessModifierOption getAccessModifierFromModifiersToken(DetailAST ast) {
        AccessModifierOption accessModifier;
        if (ast.getType() == TokenTypes.ENUM_CONSTANT_DEF) {
            accessModifier = AccessModifierOption.PUBLIC;
        }
        else {
            final DetailAST modsToken = ast.findFirstToken(TokenTypes.MODIFIERS);
            accessModifier = getAccessModifierFromModifiersTokenDirectly(modsToken);
        }

        if (accessModifier == AccessModifierOption.PACKAGE) {
            if (ScopeUtil.isInEnumBlock(ast) && ast.getType() == TokenTypes.CTOR_DEF) {
                accessModifier = AccessModifierOption.PRIVATE;
            }
            else if (ScopeUtil.isInInterfaceOrAnnotationBlock(ast)) {
                accessModifier = AccessModifierOption.PUBLIC;
            }
        }

        return accessModifier;
    }

    /**
     * Returns {@link AccessModifierOption} based on the information about access modifier
     * taken from the given token of type {@link TokenTypes#MODIFIERS}.
     *
     * @param modifiersToken token of type {@code TokenTypes#MODIFIERS}.
     * @return {@code AccessModifierOption}.
     * @throws IllegalArgumentException when expected non-null modifiersToken with type 'MODIFIERS'
     */
    private static AccessModifierOption getAccessModifierFromModifiersTokenDirectly(
            DetailAST modifiersToken) {
        if (modifiersToken == null) {
            throw new IllegalArgumentException("expected non-null AST-token with type 'MODIFIERS'");
        }

        AccessModifierOption accessModifier = AccessModifierOption.PACKAGE;
        for (DetailAST token = modifiersToken.getFirstChild(); token != null;
             token = token.getNextSibling()) {
            final int tokenType = token.getType();
            if (tokenType == TokenTypes.LITERAL_PUBLIC) {
                accessModifier = AccessModifierOption.PUBLIC;
            }
            else if (tokenType == TokenTypes.LITERAL_PROTECTED) {
                accessModifier = AccessModifierOption.PROTECTED;
            }
            else if (tokenType == TokenTypes.LITERAL_PRIVATE) {
                accessModifier = AccessModifierOption.PRIVATE;
            }
        }
        return accessModifier;
    }

    /**
     * Returns the access modifier of the surrounding "block".
     *
     * @param node the node to return the access modifier for
     * @return the access modifier of the surrounding block
     */
    public static Optional<AccessModifierOption> getSurroundingAccessModifier(DetailAST node) {
        Optional<AccessModifierOption> returnValue = Optional.empty();
        for (DetailAST token = node;
             returnValue.isEmpty() && !TokenUtil.isRootNode(token);
             token = token.getParent()) {
            final int type = token.getType();
            if (type == TokenTypes.CLASS_DEF
                || type == TokenTypes.INTERFACE_DEF
                || type == TokenTypes.ANNOTATION_DEF
                || type == TokenTypes.ENUM_DEF) {
                returnValue = Optional.ofNullable(getAccessModifierFromModifiersToken(token));
            }
            else if (type == TokenTypes.LITERAL_NEW) {
                break;
            }
        }

        return returnValue;
    }

    /**
     * Create set of class names and short class names.
     *
     * @param classNames array of class names.
     * @return set of class names and short class names.
     */
    public static Set<String> parseClassNames(String... classNames) {
        return Arrays.stream(classNames)
                .flatMap(className -> Stream.of(className, CommonUtil.baseClassName(className)))
                .filter(Predicate.not(String::isEmpty))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Strip initial newline and preceding whitespace on each line from text block content.
     * In order to be consistent with how javac handles this task, we have modeled this
     * implementation after the code from:
     * github.com/openjdk/jdk14u/blob/master/src/java.base/share/classes/java/lang/String.java
     *
     * @param textBlockContent the actual content of the text block.
     * @return string consistent with javac representation.
     */
    public static String stripIndentAndInitialNewLineFromTextBlock(String textBlockContent) {
        final String contentWithInitialNewLineRemoved =
            ALL_NEW_LINES.matcher(textBlockContent).replaceFirst("");
        final List<String> lines =
            Arrays.asList(ALL_NEW_LINES.split(contentWithInitialNewLineRemoved));
        final int indent = getSmallestIndent(lines);
        final String suffix = "";

        return lines.stream()
                .map(line -> stripIndentAndTrailingWhitespaceFromLine(line, indent))
                .collect(Collectors.joining(System.lineSeparator(), suffix, suffix));
    }

    /**
     * Helper method for stripIndentAndInitialNewLineFromTextBlock, strips correct indent
     * from string, and trailing whitespace, or returns empty string if no text.
     *
     * @param line the string to strip indent and trailing whitespace from
     * @param indent the amount of indent to remove
     * @return modified string with removed indent and trailing whitespace, or empty string.
     */
    private static String stripIndentAndTrailingWhitespaceFromLine(String line, int indent) {
        final int lastNonWhitespace = lastIndexOfNonWhitespace(line);
        String returnString = "";
        if (lastNonWhitespace > 0) {
            returnString = line.substring(indent, lastNonWhitespace);
        }
        return returnString;
    }

    /**
     * Helper method for stripIndentAndInitialNewLineFromTextBlock, to determine the smallest
     * indent in a text block string literal.
     *
     * @param lines collection of actual text block content, split by line.
     * @return number of spaces representing the smallest indent in this text block.
     */
    private static int getSmallestIndent(Collection<String> lines) {
        return lines.stream()
            .mapToInt(CommonUtil::indexOfNonWhitespace)
            .min()
            .orElse(0);
    }

    /**
     * Helper method to find the index of the last non-whitespace character in a string.
     *
     * @param line the string to find the last index of a non-whitespace character for.
     * @return the index of the last non-whitespace character.
     */
    private static int lastIndexOfNonWhitespace(String line) {
        int length;
        for (length = line.length(); length > 0; length--) {
            if (!Character.isWhitespace(line.charAt(length - 1))) {
                break;
            }
        }
        return length;
    }

    /**
     * Calculates and returns the type declaration name matching count.
     *
     * <p>
     * Suppose our pattern class is {@code foo.a.b} and class to be matched is
     * {@code foo.a.ball} then type declaration name matching count would be calculated by
     * comparing every character, and updating main counter when we hit "." to prevent matching
     * "a.b" with "a.ball". In this case type declaration name matching count
     * would be equal to 6 and not 7 (b of ball is not counted).
     * </p>
     *
     * @param patternClass class against which the given class has to be matched
     * @param classToBeMatched class to be matched
     * @return class name matching count
     */
    public static int typeDeclarationNameMatchingCount(String patternClass,
                                                       String classToBeMatched) {
        final int length = Math.min(classToBeMatched.length(), patternClass.length());
        int result = 0;
        for (int index = 0;
                index < length
                    && patternClass.charAt(index) == classToBeMatched.charAt(index);
                index++) {
            if (patternClass.charAt(index) == PACKAGE_SEPARATOR) {
                result = index;
            }
        }
        return result;
    }

    /**
     * Get the qualified name of type declaration by combining {@code packageName},
     * {@code outerClassQualifiedName} and {@code className}.
     *
     * @param packageName packageName
     * @param outerClassQualifiedName outerClassQualifiedName
     * @param className className
     * @return the qualified name of type declaration by combining {@code packageName},
     *         {@code outerClassQualifiedName} and {@code className}
     */
    public static String getQualifiedTypeDeclarationName(String packageName,
                                                         String outerClassQualifiedName,
                                                         String className) {
        final String qualifiedClassName;

        if (outerClassQualifiedName == null) {
            if (packageName == null) {
                qualifiedClassName = className;
            }
            else {
                qualifiedClassName = packageName + PACKAGE_SEPARATOR + className;
            }
        }
        else {
            qualifiedClassName = outerClassQualifiedName + PACKAGE_SEPARATOR + className;
        }
        return qualifiedClassName;
    }

    /**
     * Get name of package and super class of anon inner class by concatenating
     * the identifier values under {@link TokenTypes#DOT}.
     *
     * @param ast ast to extract superclass or package name from
     * @return qualified name
     */
    public static String extractQualifiedName(DetailAST ast) {
        return FullIdent.createFullIdent(ast).getText();
    }

    /**
     * Get the short name of super class of anonymous inner class.
     * Example:
     * {@snippet lang="text" :
     * TestClass.NestedClass obj = new Test().new NestedClass() {};
     * // Short name will be Test.NestedClass
     * }
     *
     * @param literalNewAst ast node of type {@link TokenTypes#LITERAL_NEW}
     * @return short name of base class of anonymous inner class
     */
    public static String getShortNameOfAnonInnerClass(DetailAST literalNewAst) {
        DetailAST parentAst = literalNewAst;
        while (TokenUtil.isOfType(parentAst, TokenTypes.LITERAL_NEW, TokenTypes.DOT)) {
            parentAst = parentAst.getParent();
        }
        final DetailAST firstChild = parentAst.getFirstChild();
        return extractQualifiedName(firstChild);
    }

    /**
     * Checks if the given file path is a package-info.java file.
     *
     * @param filePath path to the file.
     * @return true if the package file.
     */
    public static boolean isPackageInfo(String filePath) {
        final Path filename = Path.of(filePath).getFileName();
        return filename != null && "package-info.java".equals(filename.toString());
    }

    /**
     * Checks if a given subtree is terminated by return, throw, break, continue, or yield.
     *
     * @param ast root of given subtree
     * @return true if the subtree is terminated.
     */
    public static boolean isTerminated(final DetailAST ast) {
        return TerminationUtil.isTerminated(ast);
    }

    /**
     * This method will skip the comment content while finding the next ast of current ast.
     *
     * @param ast current ast
     * @return next ast after skipping comment
     */
    public static DetailAST getNextNonCommentAst(DetailAST ast) {
        return TokenUtil.getNextNonCommentAst(ast);
    }

}
