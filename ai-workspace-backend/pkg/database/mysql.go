package database

import (
	"time"

	"gorm.io/driver/mysql"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"

	"github.com/aiworkspace/backend/internal/config"
)

var DB *gorm.DB

func Init(cfg config.DatabaseConfig) error {
	db, err := gorm.Open(mysql.Open(cfg.DSN), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Info),
		// 禁用默认事务（提升单条写入性能约 30%）
		SkipDefaultTransaction: true,
		// 缓存预编译语句，减少解析开销
		PrepareStmt: true,
	})
	if err != nil {
		return err
	}

	sqlDB, err := db.DB()
	if err != nil {
		return err
	}

	sqlDB.SetMaxOpenConns(cfg.MaxOpenConns)
	sqlDB.SetMaxIdleConns(cfg.MaxIdleConns)
	sqlDB.SetConnMaxLifetime(time.Duration(cfg.ConnMaxLifetime) * time.Second)

	DB = db
	return nil
}
