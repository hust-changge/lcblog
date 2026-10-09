<script setup lang="ts">
// 来源：Antdv Pro / Ruyu-Blog 后台登录页。修改：删除手机号登录与 JWT 存储，改为 Session + CSRF 表单登录；
// 登录失败显示后端返回的错误（401 JSON），不重定向。
import {
  BulbFilled,
  BulbOutlined,
  LockOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { delayTimer } from '@v-c/utils'
import { loginApi } from '~/api/common/login'
import { getQueryParam } from '~/utils/tools'
import pageBubble from '@/utils/page-bubble'

const notification = useNotification()
const appStore = useAppStore()
const { layoutSetting } = storeToRefs(appStore)
const router = useRouter()
const loginModel = reactive({
  username: '',
  password: undefined as string | undefined,
  type: 'account',
})
const { t } = useI18nLocale()
const formRef = shallowRef()
const submitLoading = shallowRef(false)
const errorAlert = shallowRef(false)
const bubbleCanvas = ref<HTMLCanvasElement>()
const userStore = useUserStore()
async function submit() {
  submitLoading.value = true
  errorAlert.value = false
  try {
    await formRef.value?.validate()
    await loginApi({ username: loginModel.username, password: loginModel.password ?? '' })
    notification.success({
      message: '登录成功',
      description: '欢迎回来！',
      duration: 3,
    })
    const redirect = getQueryParam('redirect', '/')
    await userStore.getUserInfo()
    router.addRoute(await userStore.generateDynamicRoutes())
    await router.push({
      path: decodeURIComponent(redirect as string),
      replace: true,
    })
  }
  catch (e: any) {
    // 表单校验失败时 e 中带 errorFields，不需要提示
    if (!e?.errorFields)
      errorAlert.value = true
  }
  finally {
    submitLoading.value = false
  }
}

onMounted(async () => {
  await delayTimer(300)
  pageBubble.init(unref(bubbleCanvas)!)
})

onBeforeUnmount(() => {
  pageBubble.removeListeners()
})
</script>

<template>
  <div class="login-container">
    <div h-screen w-screen absolute z-10>
      <canvas ref="bubbleCanvas" />
    </div>
    <div class="login-content flex-center">
      <div class="ant-pro-form-login-main rounded">
        <!-- 登录头部 -->
        <div
          class="flex-between h-15 px-4 mb-[2px]"
        >
          <div class="flex-end">
            <span class="ant-pro-form-login-title">
              lcblog
            </span>
            <span class="ant-pro-form-login-desc">
              博客管理后台
            </span>
          </div>
          <div class="login-lang flex-center relative z-11">
            <button
              type="button"
              class="flex-center cursor-pointer text-16px theme-toggle"
              :aria-label="layoutSetting.theme === 'dark' ? '切换到亮色模式' : '切换到暗色模式'"
              @click="appStore.toggleTheme(layoutSetting.theme === 'dark' ? 'light' : 'dark')"
            >
              <!-- 亮色和暗黑模式切换按钮 -->
              <template v-if="layoutSetting.theme === 'light'">
                <BulbOutlined />
              </template>
              <template v-else>
                <BulbFilled />
              </template>
            </button>
          </div>
        </div>
        <a-divider m-0 />
        <!-- 登录主体 -->
        <div class="box-border flex min-h-[520px]">
          <!-- 登录框左侧 -->
          <div class="ant-pro-form-login-main-left min-h-[520px] flex-center  bg-[var(--bg-color-container)]">
            <img src="@/assets/images/login-left.png" class="h-5/6 w-5/6">
          </div>
          <a-divider m-0 type="vertical" class="ant-pro-login-divider  min-h-[520px]" />
          <!-- 登录框右侧 -->
          <div class="ant-pro-form-login-main-right px-5 w-[335px] flex-center flex-col relative z-11">
            <div class="text-center py-6 text-2xl">
              欢迎登录后台系统
            </div>
            <a-form ref="formRef" :model="loginModel">
              <a-tabs v-model:activeKey="loginModel.type" centered>
                <a-tab-pane key="account" :tab="t('pages.login.accountLogin.tab')" />
              </a-tabs>
              <!-- 判断是否存在error -->
              <a-alert
                v-if="errorAlert" mb-24px
                message="用户名或密码错误" type="error" show-icon
              />
              <template v-if="loginModel.type === 'account'">
                <a-form-item name="username" :rules="[{ required: true, message: t('pages.login.username.required') }]">
                  <a-input
                    v-model:value="loginModel.username" allow-clear
                    autocomplete="username" aria-label="用户名"
                    :placeholder="t('pages.login.username.placeholder')" size="large" @press-enter="submit"
                  >
                    <template #prefix>
                      <UserOutlined />
                    </template>
                  </a-input>
                </a-form-item>
                <a-form-item name="password" :rules="[{ required: true, message: t('pages.login.password.required') }]">
                  <a-input-password
                    v-model:value="loginModel.password" allow-clear aria-label="密码"
                    :placeholder="t('pages.login.password.placeholder')" size="large" autocomplete="current-password"
                    @press-enter="submit"
                  >
                    <template #prefix>
                      <LockOutlined />
                    </template>
                  </a-input-password>
                </a-form-item>
              </template>
              <a-button type="primary" block :loading="submitLoading" size="large" @click="submit">
                {{ t('pages.login.submit') }}
              </a-button>
            </a-form>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="less" scoped>
.login-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: auto;
  background: var(--bg-color-container);
}

.login-lang {
  height: 40px;
  line-height: 44px;

  .theme-toggle {
    background: none;
    border: 0;
    color: inherit;
  }
}

.login-content {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
}

.ant-pro-form-login-container {
  display: flex;
  flex: 1 1;
  flex-direction: column;
  height: 100%;
  padding: 32px 0;
  overflow: auto;
  background: inherit
}

.ant-pro-form-login-header a {
  text-decoration: none
}

.ant-pro-form-login-title {
  color: var(--text-color);
  font-weight: 600;
  font-size: 33px;
  line-height: 1;
}

.ant-pro-form-login-logo {
  width: 44px;
  height: 44px;
  margin-right: 16px;
  vertical-align: top
}

.ant-pro-form-login-desc {
  color: var(--text-color-1);
  font-size: 14px;
  margin-left: 16px
}

.ant-pro-form-login-main-right {
  .ant-tabs-nav-list {
    margin: 0 auto;
    font-size: 16px;
  }

  .ant-pro-form-login-other {
    line-height: 22px;
    text-align: center
  }

}

.ant-pro-form-login-main {
  box-shadow: var(--c-shadow);
}

.icon {
  margin-left: 8px;
  color: var(--text-color-2);
  font-size: 24px;
  vertical-align: middle;
  cursor: pointer;
  transition: color .3s;

  &:hover {
    color: var(--pro-ant-color-primary);
  }
}

.login-media(@width:100%) {
  .ant-pro-form-login-main {
    width: @width;
  }
  .ant-pro-form-login-main-left {
    display: none;
  }
  .ant-pro-form-login-main-right {
    width: 100%;
  }
  .ant-pro-form-login-desc {
    display: none;
  }
}

@media (min-width: 992px) {
  .ant-pro-form-login-main-left {
    width: 700px;
  }
}

@media (min-width: 768px) and (max-width: 991px) {
  .ant-pro-login-divider {
    display: none;
  }

  .login-media(400px)
}

@media screen and (max-width: 767px) {
  .login-media(350px);

  .ant-pro-login-divider {
    display: none;
  }
}
</style>
