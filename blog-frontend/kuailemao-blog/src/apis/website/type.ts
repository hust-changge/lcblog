export interface Project {
    name: string
    description: string
    url: string
}

export interface Profile {
    name: string
    intro: string
    research: string
    techStack: string[]
    projects: Project[]
    github: string
    avatar: string
    background: string
    announcement: string
    startDate: string
}

export default interface WebsiteInfo {
    title: string
    description: string
    profile: Profile
    articleCount: number
    categoryCount: number
    tagCount: number
}
