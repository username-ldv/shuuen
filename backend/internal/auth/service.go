package auth

import (
	"errors"
	"strconv"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"golang.org/x/crypto/bcrypt"

	"shuuen-backend/internal/config"
	"shuuen-backend/internal/model"
)

type Service struct {
	secret []byte
	issuer string
	ttl    time.Duration
}

type Claims struct {
	UserID       uint   `json:"user_id"`
	Username     string `json:"username"`
	Role         string `json:"role"`
	TokenVersion uint   `json:"token_version"`
	jwt.RegisteredClaims
}

func NewService(cfg config.AuthConfig) *Service {
	return &Service{
		secret: []byte(cfg.JWTSecret),
		issuer: cfg.JWTIssuer,
		ttl:    cfg.AccessTokenTTL,
	}
}

func HashPassword(password string) (string, error) {
	hash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return "", err
	}
	return string(hash), nil
}

func CheckPassword(hash string, password string) bool {
	return bcrypt.CompareHashAndPassword([]byte(hash), []byte(password)) == nil
}

func (s *Service) GenerateAccessToken(user model.User) (string, time.Time, error) {
	now := time.Now().UTC()
	expiresAt := now.Add(s.ttl)

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, Claims{
		UserID:       user.ID,
		Username:     user.Username,
		Role:         user.Role,
		TokenVersion: user.TokenVersion,
		RegisteredClaims: jwt.RegisteredClaims{
			Subject:   strconv.FormatUint(uint64(user.ID), 10),
			Issuer:    s.issuer,
			IssuedAt:  jwt.NewNumericDate(now),
			ExpiresAt: jwt.NewNumericDate(expiresAt),
		},
	})

	signed, err := token.SignedString(s.secret)
	if err != nil {
		return "", time.Time{}, err
	}
	return signed, expiresAt, nil
}

func (s *Service) ParseAccessToken(raw string) (*Claims, error) {
	token, err := jwt.ParseWithClaims(raw, &Claims{}, func(token *jwt.Token) (any, error) {
		if token.Method != jwt.SigningMethodHS256 {
			return nil, errors.New("unexpected signing method")
		}
		return s.secret, nil
	}, jwt.WithIssuer(s.issuer))
	if err != nil {
		return nil, err
	}

	claims, ok := token.Claims.(*Claims)
	if !ok || !token.Valid {
		return nil, errors.New("invalid token")
	}
	return claims, nil
}
