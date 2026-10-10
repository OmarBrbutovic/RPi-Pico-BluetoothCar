from machine import Pin, PWM, UART
import utime

# Define constants
FORWARD = 'forward'
BACKWARD = 'backward'
RIGHT = 'right'
LEFT = 'left'
UP_RIGHT = 'upRight'
UP_LEFT = 'upLeft'
DOWN_RIGHT = 'downRight'
DOWN_LEFT = 'downLeft'
STOP = 'stop'

# Define UART channel and set baud rate to 9600
uart = UART(0, 9600)

# Define pins for control of the DC motors
MOTOR_PINS = [6, 7, 4, 3]
In1, In2, In3, In4 = [Pin(pin, Pin.OUT) for pin in MOTOR_PINS]

# Define PWM channels for the enable pins of the DC motors
EN_A = PWM(Pin(8))
EN_B = PWM(Pin(2))

# Set the frequency and duty cycle of the PWM signals on the enable pins
EN_A.freq(1500)
EN_B.freq(1500)
EN_A.duty_u16(65025)
EN_B.duty_u16(65025)

# Define pins for control of the ultrasonic sensor
trigger = Pin(21, Pin.OUT)
echo = Pin(20, Pin.IN)

# Set crash prevention default value to True and turn on the LED
crash_prevention = True
LED = Pin(25, Pin.OUT)
LED.high()

# Define function to measure distance using ultrasonic sensor
def ultra():
    trigger.low()
    utime.sleep_us(2)
    trigger.high()
    utime.sleep_us(5)
    trigger.low()

    while echo.value() == 0:
        signaloff = utime.ticks_us()

    while echo.value() == 1:
        signalon = utime.ticks_us()

    timepassed = signalon - signaloff
    distance = (timepassed * 0.0343) / 2
    print(distance)

    if crash_prevention and distance < 20:
        if 'backward' in data:
            move('backward')
        else:
            move('stop')

    return distance


# Define function to move the robot in a specified direction
def move(direction):
    directions = {
        FORWARD: (In1.high, In2.low, In3.low, In4.high),
        BACKWARD: (In1.low, In2.high, In3.high, In4.low),
        RIGHT: (In1.low, In2.high, In3.low, In4.high),
        LEFT: (In1.high, In2.low, In3.high, In4.low),
        UP_RIGHT: (In1.low, In2.low, In3.low, In4.high),
        UP_LEFT: (In1.high, In2.low, In3.low, In4.low),
        DOWN_RIGHT: (In1.high, In2.high, In3.high, In4.low),
        DOWN_LEFT: (In1.low, In2.high, In3.high, In4.high),
        STOP: (In1.low, In2.low, In3.low, In4.low),
    }
    for pin_action in directions.get(direction, directions[STOP]):
        pin_action()


data = ""  # Initialize data to an empty string

while True:
    dist = ultra()
    if uart.any():
        data = uart.read().decode('utf-8')
        print(data)

        if ('HC-SR04_ON' in data):
            crash_prevention = True
            LED.high()
        elif ('HC-SR04_OFF' in data):
            crash_prevention = False
            LED.low()
        else:
            pass

        if ('E' in data):
            speed = data.split("|")
            print(speed[1])
            set_speed = float(speed[1])/100 * 65025
            EN_A.duty_u16(int(set_speed))
            EN_B.duty_u16(int(set_speed))

        move(data)
