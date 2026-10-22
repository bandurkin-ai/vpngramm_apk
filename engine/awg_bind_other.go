//go:build !android

package main

import "github.com/amnezia-vpn/amneziawg-go/conn"

// Вне Android (стенд): сокеты помечаются SO_MARK 1 — по этой метке тестовое «приложение» ведёт их мимо туннеля.
func newProtectedBind() conn.Bind { return conn.NewStdNetBind() }

func bindUAPI() string { return "fwmark=1\n" }
