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

import java.util.regex.Pattern;

import com.puppycrawl.tools.checkstyle.api.TokenTypes;

/**
 * Interprets Java numeric literals.
 */
final class NumericLiteralUtil {

    // constants for parseDouble()
    /** Binary radix. */
    private static final int BASE_2 = 2;

    /** Octal radix. */
    private static final int BASE_8 = 8;

    /** Decimal radix. */
    private static final int BASE_10 = 10;

    /** Hex radix. */
    private static final int BASE_16 = 16;

    /** Pattern matching underscore characters ('_'). */
    private static final Pattern UNDERSCORE_PATTERN = Pattern.compile("_");

    /** Prevents instances. */
    private NumericLiteralUtil() {
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
    static double parseDouble(String text, int type) {
        String txt = UNDERSCORE_PATTERN.matcher(text).replaceAll("");

        return switch (type) {
            case TokenTypes.NUM_FLOAT, TokenTypes.NUM_DOUBLE -> Double.parseDouble(txt);

            case TokenTypes.NUM_INT, TokenTypes.NUM_LONG -> {
                int radix = BASE_10;
                if (txt.startsWith("0x") || txt.startsWith("0X")) {
                    radix = BASE_16;
                    txt = txt.substring(2);
                }
                else if (txt.startsWith("0b") || txt.startsWith("0B")) {
                    radix = BASE_2;
                    txt = txt.substring(2);
                }
                else if (txt.startsWith("0")) {
                    radix = BASE_8;
                }
                yield parseNumber(txt, radix, type);
            }

            default -> Double.NaN;
        };
    }

    /**
     * Parses the string argument as an integer or a long in the radix specified by
     * the second argument. The characters in the string must all be digits of
     * the specified radix.
     *
     * @param text the String containing the integer representation to be
     *     parsed. Precondition: text contains a parsable int.
     * @param radix the radix to be used while parsing text.
     * @param type the token type of the text. Should be a constant of
     *     {@link TokenTypes}.
     * @return the number represented by the string argument in the specified radix.
     */
    private static double parseNumber(final String text, final int radix, final int type) {
        String txt = text;
        if (txt.endsWith("L") || txt.endsWith("l")) {
            txt = txt.substring(0, txt.length() - 1);
        }
        final double result;

        final boolean negative = txt.charAt(0) == '-';
        if (type == TokenTypes.NUM_INT) {
            if (negative) {
                result = Integer.parseInt(txt, radix);
            }
            else {
                result = Integer.parseUnsignedInt(txt, radix);
            }
        }
        else {
            if (negative) {
                result = Long.parseLong(txt, radix);
            }
            else {
                result = Long.parseUnsignedLong(txt, radix);
            }
        }

        return result;
    }

}
