package main

import (
	"fmt"
	"strings"

	"github.com/johansundell/advent_of_code_2017/johansundell-go/adventofcode2017"
)

func main() {
	data, err := adventofcode2017.GetInput("day2.txt")
	if err != nil {
		panic(err)
	}
	fmt.Println(makeChecksum(strings.Split(data, "\n")))
}

func makeChecksum(str []string) int {
	a, b := 0, 0
	for _, s := range str {
		x, y := countStr(s)
		if x {
			a++
		}
		if y {
			b++
		}
	}
	return a * b
}

func countStr(str string) (found2, found3 bool) {
	for i := 0; len(str) > 0; i++ {
		a := str[0]
		if strings.Count(str, string(a)) == 3 {
			found3 = true
		}
		if strings.Count(str, string(a)) == 2 {
			found2 = true
		}
		str = strings.Replace(str, string(a), "", -1)
	}
	return
}
