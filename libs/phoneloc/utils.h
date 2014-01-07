/*
 * Copyright (C) 2012 - 2014 The MoKee OpenSource Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

#include "stdio.h"
#include "string.h"
#include "stdlib.h"

void int2str(char *str, int integer) {
    int tmp_int = integer, tmp_int_len = 0;
    char *p = str;
    while (0 != tmp_int)
        tmp_int /= 10, tmp_int_len++;
    tmp_int = integer;
    int i = 0, len_zero_count = 1;
    char tmp = 0;
    for (i = 0; i < tmp_int_len; i++)
        len_zero_count *= 10;
    for (i = 0; i < tmp_int_len; i++) {
        len_zero_count /= 10;
        if (0 == i)
            tmp = tmp_int / len_zero_count + '0';
        else
            tmp = tmp_int / len_zero_count % 10 + '0';
        *p++ = tmp;
    }
}

void str2int(char *str, int *integer, int count) {
    int tmp_int = 0, zero_count = 1;
    char *p = str;
    int i = 0;
    for (i = 0; i < count; i++)
        zero_count *= 10;
    while (1 != zero_count) {
        zero_count /= 10;
        tmp_int += (*p++ - '0') * zero_count;
    }
    *integer = tmp_int;
}

int property_get(const char *key, char *value, const char *default_value) {
    int len;

    len = __system_property_get(key, value);
    if (len > 0) {
        return len;
    }

    if (default_value) {
        len = strlen(default_value);
        memcpy(value, default_value, len + 1);
    }
    return len;
}

void toOriginal(char *str, char *dstr) {
    int i = 0, code = 0, value = 0, len = strlen(str);
    char *key = malloc(100), tmpvalue = 0, resultkey[100] = { 0 };
    memset(key, 0, 100);
    for (i = 0; i < len; i++) {
        code = *str++;
        code = code + (i + 1) * 17 % 7;
        int2str(key, code);
        str2int(key, &value, strlen(key));
        memset(key, 0, 100);
        tmpvalue = (char) value;
        resultkey[i] = tmpvalue;
        value = 0;
    }
    free(key);
    memcpy(dstr, resultkey, strlen(resultkey) + 1);
}
