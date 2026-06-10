package redis

import (
	"context"
	"fmt"

	"github.com/redis/go-redis/v9"

	"github.com/aiworkspace/backend/internal/config"
)

var Client *redis.Client

func Init(cfg config.RedisConfig) error {
	Client = redis.NewClient(&redis.Options{
		Addr:     cfg.Addr,
		Password: cfg.Password,
		DB:       cfg.DB,
	})

	if err := Client.Ping(context.Background()).Err(); err != nil {
		return fmt.Errorf("redis ping: %w", err)
	}
	return nil
}
