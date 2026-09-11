http://localhost:8083/h2-console

sudo systemctl stop kesl

**instalar mosquito linux** 

sudo apt update 

sudo apt install mosquitto mosquitto-clients



**configurra mosquitto** 

hostname -I

sudo nano /etc/mosquitto/conf.d/default.conf

listener 1883 192.168.110.229
allow_anonymous true

sudo systemctl restart mosquitto

listener 1883 10.200.166.2

--------------update-----------

mosquitto_sub -h 192.168.10.89 -p 1883 -t '#' -v