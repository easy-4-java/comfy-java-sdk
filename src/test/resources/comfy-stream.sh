#!/bin/sh
printf '%s\n' '{"schema":"event/1","type":"queued","prompt_id":"p1"}'
sleep 1
printf '%s\n' '{"schema":"envelope/1","type":"envelope","ok":true,"command":"run"}'
