/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

#include <proton/message.h>
#include <proton/buffer.h>
#include <proton/codec.h>
#include <proton/error.h>
#include <proton/parser.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include "protocol.h"
#include "../util.h"

ssize_t pn_message_data(char *dst, size_t available, const char *src, size_t size)
{
  pn_bytes_t bytes = pn_bytes(available, dst);
  pn_atom_t buf[16];
  pn_atoms_t atoms = {16, buf};

  int err = pn_fill_atoms(&atoms, "DLz", 0x75, size, src);
  if (err) return err;
  err = pn_encode_atoms(&bytes, &atoms);
  if (err) return err;
  return bytes.size;
}

// message

struct pn_message_t {
  bool durable;
  uint8_t priority;
  pn_millis_t ttl;
  bool first_acquirer;
  uint32_t delivery_count;
  pn_atom_t id;
  pn_buffer_t *user_id;
  pn_buffer_t *address;
  pn_buffer_t *subject;
  pn_buffer_t *reply_to;
  pn_atom_t correlation_id;
  pn_buffer_t *content_type;
  pn_buffer_t *content_encoding;
  pn_timestamp_t expiry_time;
  pn_timestamp_t creation_time;
  pn_buffer_t *group_id;
  pn_sequence_t group_sequence;
  pn_buffer_t *reply_to_group_id;

  pn_data_t *data;

  pn_parser_t *parser;

  pn_section_t *section_head;
  pn_section_t *section_tail;
};

struct pn_section_t {
  pn_message_t *message;
  pn_section_t *section_next;
  pn_section_t *section_prev;
  pn_format_t format;
  pn_data_t *data;
  bool cleared;
};

pn_message_t *pn_message()
{
  pn_message_t *msg = malloc(sizeof(pn_message_t));
  msg->durable = false;
  msg->priority = PN_DEFAULT_PRIORITY;
  msg->ttl = 0;
  msg->first_acquirer = false;
  msg->delivery_count = 0;
  msg->id.type = PN_NULL;
  msg->user_id = NULL;
  msg->address = NULL;
  msg->subject = NULL;
  msg->reply_to = NULL;
  msg->correlation_id.type = PN_NULL;
  msg->content_type = NULL;
  msg->content_encoding = NULL;
  msg->expiry_time = 0;
  msg->creation_time = 0;
  msg->group_id = NULL;
  msg->group_sequence = 0;
  msg->reply_to_group_id = NULL;
  msg->data = NULL;
  msg->parser = NULL;
  msg->section_head = NULL;
  msg->section_tail = NULL;
  return msg;
}

void pn_message_free(pn_message_t *msg)
{
  if (msg) {
    pn_buffer_free(msg->user_id);
    pn_buffer_free(msg->address);
    pn_buffer_free(msg->subject);
    pn_buffer_free(msg->reply_to);
    pn_buffer_free(msg->content_type);
    pn_buffer_free(msg->content_encoding);
    pn_buffer_free(msg->group_id);
    pn_buffer_free(msg->reply_to_group_id);
    pn_data_free(msg->data);
    free(msg);
  }
}

void pn_message_clear(pn_message_t *msg)
{
  msg->durable = false;
  msg->priority = PN_DEFAULT_PRIORITY;
  msg->ttl = 0;
  msg->first_acquirer = false;
  msg->delivery_count = 0;
  msg->id.type = PN_NULL;
  if (msg->user_id) pn_buffer_clear(msg->user_id);
  if (msg->address) pn_buffer_clear(msg->address);
  if (msg->subject) pn_buffer_clear(msg->subject);
  if (msg->reply_to) pn_buffer_clear(msg->reply_to);
  msg->correlation_id.type = PN_NULL;
  if (msg->content_type) pn_buffer_clear(msg->content_type);
  if (msg->content_encoding) pn_buffer_clear(msg->content_encoding);
  msg->expiry_time = 0;
  msg->creation_time = 0;
  if (msg->group_id) pn_buffer_clear(msg->group_id);
  msg->group_sequence = 0;
  if (msg->reply_to_group_id) pn_buffer_clear(msg->reply_to_group_id);
  if (msg->data) pn_data_clear(msg->data);
}

int pn_message_errno(pn_message_t *msg)
{
  if (msg && msg->parser) {
    return pn_parser_errno(msg->parser);
  } else {
    return 0;
  }
}

const char *pn_message_error(pn_message_t *msg)
{
  if (msg && msg->parser) {
    return pn_parser_error(msg->parser);
  } else {
    return NULL;
  }
}

pn_parser_t *pn_message_parser(pn_message_t *msg)
{
  if (!msg->parser) {
    msg->parser = pn_parser();
  }
  return msg->parser;
}

bool pn_message_is_durable(pn_message_t *msg)
{
  return msg ? msg->durable : false;
}
int pn_message_set_durable(pn_message_t *msg, bool durable)
{
  if (!msg) return PN_ARG_ERR;
  msg->durable = durable;
  return 0;
}


uint8_t pn_message_get_priority(pn_message_t *msg)
{
  return msg ? msg->priority : PN_DEFAULT_PRIORITY;
}
int pn_message_set_priority(pn_message_t *msg, uint8_t priority)
{
  if (!msg) return PN_ARG_ERR;
  msg->priority = priority;
  return 0;
}

pn_millis_t pn_message_get_ttl(pn_message_t *msg)
{
  return msg ? msg->ttl : 0;
}
int pn_message_set_ttl(pn_message_t *msg, pn_millis_t ttl)
{
  if (!msg) return PN_ARG_ERR;
  msg->ttl = ttl;
  return 0;
}

bool pn_message_is_first_acquirer(pn_message_t *msg)
{
  return msg ? msg->first_acquirer : false;
}
int pn_message_set_first_acquirer(pn_message_t *msg, bool first)
{
  if (!msg) return PN_ARG_ERR;
  msg->first_acquirer = first;
  return 0;
}

uint32_t pn_message_get_delivery_count(pn_message_t *msg)
{
  return msg ? msg->delivery_count : 0;
}
int pn_message_set_delivery_count(pn_message_t *msg, uint32_t count)
{
  if (!msg) return PN_ARG_ERR;
  msg->delivery_count = count;
  return 0;
}

pn_atom_t pn_message_get_id(pn_message_t *msg)
{
  return msg ? msg->id : (pn_atom_t) {.type=PN_NULL};
}
int pn_message_set_id(pn_message_t *msg, pn_atom_t id)
{
  if (!msg) return PN_ARG_ERR;

  msg->id = id;
  return 0;
}

static int pn_buffer_set_bytes(pn_buffer_t **buf, pn_bytes_t bytes)
{
  if (!*buf) {
    *buf = pn_buffer(64);
  }

  int err = pn_buffer_clear(*buf);
  if (err) return err;

  return pn_buffer_append(*buf, bytes.start, bytes.size);
}

static const char *pn_buffer_str(pn_buffer_t *buf)
{
  if (buf) {
    pn_bytes_t bytes = pn_buffer_bytes(buf);
    if (bytes.size) {
      return bytes.start;
    }
  }

  return NULL;
}

static int pn_buffer_set_strn(pn_buffer_t **buf, const char *str, size_t size)
{
  if (!*buf) {
    *buf = pn_buffer(64);
  }

  int err = pn_buffer_clear(*buf);
  if (err) return err;
  err = pn_buffer_append(*buf, str, size);
  if (err) return err;
  if (str && str[size-1]) {
    return pn_buffer_append(*buf, "\0", 1);
  } else {
    return 0;
  }
}

static int pn_buffer_set_str(pn_buffer_t **buf, const char *str)
{
  size_t size = str ? strlen(str) + 1 : 0;
  return pn_buffer_set_strn(buf, str, size);
}

pn_bytes_t pn_message_get_user_id(pn_message_t *msg)
{
  return msg && msg->user_id ? pn_buffer_bytes(msg->user_id) : pn_bytes(0, NULL);
}
int pn_message_set_user_id(pn_message_t *msg, pn_bytes_t user_id)
{
  if (!msg) return PN_ARG_ERR;
  return pn_buffer_set_bytes(&msg->user_id, user_id);
}

const char *pn_message_get_address(pn_message_t *msg)
{
  return msg ? pn_buffer_str(msg->address) : NULL;
}
int pn_message_set_address(pn_message_t *msg, const char *address)
{
  if (!msg) return PN_ARG_ERR;
  return pn_buffer_set_str(&msg->address, address);
}

const char *pn_message_get_subject(pn_message_t *msg)
{
  return msg ? pn_buffer_str(msg->subject) : NULL;
}
int pn_message_set_subject(pn_message_t *msg, const char *subject)
{
  if (!msg) return PN_ARG_ERR;
  return pn_buffer_set_str(&msg->subject, subject);
}

const char *pn_message_get_reply_to(pn_message_t *msg)
{
  return msg ? pn_buffer_str(msg->reply_to) : NULL;
}
int pn_message_set_reply_to(pn_message_t *msg, const char *reply_to)
{
  if (!msg) return PN_ARG_ERR;
  return pn_buffer_set_str(&msg->reply_to, reply_to);
}

pn_atom_t pn_message_get_correlation_id(pn_message_t *msg)
{
  return msg ? msg->correlation_id : (pn_atom_t) {.type=PN_NULL};
}
int pn_message_set_correlation_id(pn_message_t *msg, pn_atom_t atom)
{
  if (!msg) return PN_ARG_ERR;

  msg->correlation_id = atom;
  return 0;
}

const char *pn_message_get_content_type(pn_message_t *msg)
{
  return msg ? pn_buffer_str(msg->content_type) : NULL;
}
int pn_message_set_content_type(pn_message_t *msg, const char *type)
{
  if (!msg) return PN_ARG_ERR;
  return pn_buffer_set_str(&msg->content_type, type);
}

const char *pn_message_get_content_encoding(pn_message_t *msg)
{
  return msg ? pn_buffer_str(msg->content_encoding) : NULL;
}
int pn_message_set_content_encoding(pn_message_t *msg, const char *encoding)
{
  if (!msg) return PN_ARG_ERR;
  return pn_buffer_set_str(&msg->content_encoding, encoding);
}

pn_timestamp_t pn_message_get_expiry_time(pn_message_t *msg)
{
  return msg ? msg->expiry_time : 0;
}
int pn_message_set_expiry_time(pn_message_t *msg, pn_timestamp_t time)
{
  if (!msg) return PN_ARG_ERR;
  msg->expiry_time = time;
  return 0;
}

pn_timestamp_t pn_message_get_creation_time(pn_message_t *msg)
{
  return msg ? msg->creation_time : 0;
}
int pn_message_set_creation_time(pn_message_t *msg, pn_timestamp_t time)
{
  if (!msg) return PN_ARG_ERR;
  msg->creation_time = time;
  return 0;
}

const char *pn_message_get_group_id(pn_message_t *msg)
{
  return msg ? pn_buffer_str(msg->group_id) : NULL;
}
int pn_message_set_group_id(pn_message_t *msg, const char *group_id)
{
  if (!msg) return PN_ARG_ERR;
  return pn_buffer_set_str(&msg->group_id, group_id);
}

pn_sequence_t pn_message_get_group_sequence(pn_message_t *msg)
{
  return msg ? msg->group_sequence : 0;
}
int pn_message_set_group_sequence(pn_message_t *msg, pn_sequence_t n)
{
  if (!msg) return PN_ARG_ERR;
  msg->group_sequence = n;
  return 0;
}

const char *pn_message_get_reply_to_group_id(pn_message_t *msg)
{
  return msg ? pn_buffer_str(msg->reply_to_group_id) : NULL;
}
int pn_message_set_reply_to_group_id(pn_message_t *msg, const char *reply_to_group_id)
{
  if (!msg) return PN_ARG_ERR;
  return pn_buffer_set_str(&msg->reply_to_group_id, reply_to_group_id);
}

int pn_message_decode(pn_message_t *msg, pn_format_t format, const char *bytes, size_t size)
{
  if (!msg || format != PN_AMQP || !bytes || !size) return PN_ARG_ERR;

  while (size) {
    if (!msg->data) {
      msg->data = pn_data(64);
    }
    size_t copy = size;
    pn_data_clear(msg->data);
    int err = pn_data_decode(msg->data, (char *) bytes, &copy);
    if (err) return err;
    size -= copy;
    bytes += copy;
    bool scanned;
    uint64_t desc;
    err = pn_data_scan(msg->data, "D?L.", &scanned, &desc);
    if (err) return err;
    if (!scanned){
      desc = 0;
    }

    switch (desc) {
    case HEADER:
      pn_data_scan(msg->data, "D.[oBIoI]", &msg->durable, &msg->priority,
                   &msg->ttl, &msg->first_acquirer, &msg->delivery_count);
      break;
    case PROPERTIES:
      {
        pn_bytes_t user_id, address, subject, reply_to, ctype, cencoding,
          group_id, reply_to_group_id;
        err = pn_data_scan(msg->data, "D.[.zSSS.ssLLSiS]", &user_id, &address,
                           &subject, &reply_to, &ctype, &cencoding,
                           &msg->expiry_time, &msg->creation_time, &group_id,
                           &msg->group_sequence, &reply_to_group_id);
        if (err) return err;
        err = pn_buffer_set_bytes(&msg->user_id, user_id);
        if (err) return err;
        err = pn_buffer_set_strn(&msg->address, address.start, address.size);
        if (err) return err;
        err = pn_buffer_set_strn(&msg->subject, subject.start, subject.size);
        if (err) return err;
        err = pn_buffer_set_strn(&msg->reply_to, reply_to.start, reply_to.size);
        if (err) return err;
        err = pn_buffer_set_strn(&msg->content_type, ctype.start, ctype.size);
        if (err) return err;
        err = pn_buffer_set_strn(&msg->content_encoding, cencoding.start,
                                 cencoding.size);
        if (err) return err;
        err = pn_buffer_set_strn(&msg->group_id, group_id.start, group_id.size);
        if (err) return err;
        err = pn_buffer_set_strn(&msg->reply_to_group_id, reply_to_group_id.start,
                                 reply_to_group_id.size);
        if (err) return err;
      }
      break;
    default:
      {
        pn_section_t *section = msg->section_head;
        if (!section) {
          section = pn_section(msg);
        }
        pn_data_t *data = section->data;
        section->data = msg->data;
        msg->data = data;
      }
      break;
    }
  }

  return pn_data_clear(msg->data);
}

int pn_section_encode(pn_section_t *section, char *data, size_t *size);

int pn_message_encode(pn_message_t *msg, pn_format_t format, char *bytes, size_t *size)
{
  if (!msg || format != PN_AMQP || !bytes || !size || !*size) return PN_ARG_ERR;
  if (!msg->data) {
    msg->data = pn_data(64);
  }

  int err = pn_data_clear(msg->data);
  if (err) return err;

  err = pn_data_fill(msg->data, "DL[oBIoI]", HEADER, msg->durable,
                     msg->priority, msg->ttl, msg->first_acquirer,
                     msg->delivery_count);
  if (err) return err;

  err = pn_data_fill(msg->data, "DL[nzSSSnssLLSiS]", PROPERTIES,
                     pn_buffer_bytes(msg->user_id),
                     pn_buffer_str(msg->address),
                     pn_buffer_str(msg->subject),
                     pn_buffer_str(msg->reply_to),
                     pn_buffer_str(msg->content_type),
                     pn_buffer_str(msg->content_encoding),
                     msg->expiry_time,
                     msg->creation_time,
                     pn_buffer_str(msg->group_id),
                     msg->group_sequence,
                     pn_buffer_str(msg->reply_to_group_id));
  if (err) return err;

  size_t remaining = *size;
  size_t encoded = remaining;

  err = pn_data_encode(msg->data, bytes, &encoded);
  if (err) return err;

  bytes += encoded;
  remaining -= encoded;

  pn_section_t *section = msg->section_head;
  while (section) {
    encoded = remaining;
    err = pn_section_encode(section, bytes, &encoded);
    if (err) return err;
    bytes += encoded;
    remaining -= encoded;
    section = section->section_next;
  }

  *size -= remaining;

  return 0;
}

pn_section_t *pn_section(pn_message_t *message)
{
  pn_section_t *section = malloc(sizeof(pn_section_t));
  if (!section) return section;
  section->message = message;
  LL_ADD(message, section, section);
  section->format = PN_AMQP;
  section->data = NULL;
  return section;
}

void pn_section_free(pn_section_t *section)
{
  if (section) {
    LL_REMOVE(section->message, section, section);
    free(section);
  }
}

void pn_section_clear(pn_section_t *section)
{
  if (!section) return;

  if (section->data) {
    pn_data_clear(section->data);
  }
}

const char *pn_section_error(pn_section_t *section)
{
  if (section) {
    return pn_message_error(section->message);
  } else {
    return NULL;
  }
}

pn_format_t pn_section_get_format(pn_section_t *section)
{
  return section ? section->format : PN_AMQP;
}

int pn_section_set_format(pn_section_t *section, pn_format_t format)
{
  if (!section) return PN_ARG_ERR;

  section->format = format;
  return 0;
}

int pn_section_load(pn_section_t *section, const char *data)
{
  if (!section) return PN_ARG_ERR;

  if (!section->data) {
    section->data = pn_data(16);
  }

  pn_parser_t *parser = pn_message_parser(section->message);

  while (true) {
    pn_data_clear(section->data);
    pn_atoms_t atoms = pn_data_available(section->data);
    int err = pn_parser_parse(parser, data, &atoms);
    if (err == PN_OVERFLOW) {
      err = pn_data_grow(section->data);
      if (err) return err;
      continue;
    } else if (err) {
      return err;
    } else {
      return pn_data_resize(section->data, atoms.size);
    }
  }
}

int pn_section_save(pn_section_t *section, char *data, size_t *size)
{
  if (!section) return PN_ARG_ERR;

  if (!section->data) {
    *size = 0;
    return 0;
  }

  return pn_data_format(section->data, data, size);
}

int pn_section_encode(pn_section_t *section, char *data, size_t *size)
{
  if (!section || !data || !size || !*size) return PN_ARG_ERR;

  if (!section->data) {
    *size = 0;
    return 0;
  }

  return pn_data_encode(section->data, data, size);
}
